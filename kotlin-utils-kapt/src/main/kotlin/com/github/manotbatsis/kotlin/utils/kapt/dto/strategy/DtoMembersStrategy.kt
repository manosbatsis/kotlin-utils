package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manotbatsis.kotlin.utils.api.DefaultValue
import com.github.manotbatsis.kotlin.utils.api.DtoInsufficientMappingException
import com.github.manotbatsis.kotlin.utils.kapt.dto.strategy.DtoMembersStrategy.Statement
import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget.FIELD
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.TypeSpec.Builder
import javax.lang.model.element.VariableElement

/**
 * Used optionally to implement delegates
 * for a subset of [DtoTypeStrategy]
 */
interface DtoMembersStrategy: ProcessingEnvironmentAware {
    data class Statement(val format: String, val args: Array<Any?> = emptyArray())

    /** Override to modify processing of individual fields */
    fun processFields(typeSpecBuilder: Builder, fields: List<VariableElement>)

    /** Override to change the property-level annotations applied   */
    fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, variableElement: VariableElement)
    fun getToPatchedFunctionBuilder(
            originalTypeParameter: ParameterSpec
    ): FunSpec.Builder

    fun getToTargetTypeFunctionBuilder(): FunSpec.Builder
    fun toPropertyName(variableElement: VariableElement): String
    fun toPropertyTypeName(variableElement: VariableElement): TypeName
    fun toDefaultValueExpression(variableElement: VariableElement): String
    fun toMapStatement(variableElement: VariableElement, commaOrEmpty: String): Statement?
    fun toPatchStatement(variableElement: VariableElement, commaOrEmpty: String): Statement?
    fun toAltConstructorStatement(index: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName, commaOrEmpty: String): Statement?
    fun toPropertySpecBuilder(index: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName): PropertySpec.Builder
    fun fieldProcessed(index: Int, originalProperty: VariableElement, propertyName: String, propertyType: TypeName)
}


open class SimpleDtoMembersStrategy(
        val annotatedElementInfo: AnnotatedElementInfo
) : DtoMembersStrategy, AnnotatedElementInfo by annotatedElementInfo {


    // Original type parameter, used in alt constructor and util functions
    val originalTypeParameter = ParameterSpec.builder("original", primaryTargetTypeElement.asKotlinTypeName()).build()

    // Create DTO primary constructor
    val dtoConstructorBuilder = FunSpec.constructorBuilder()

    // Create DTO alternative constructor
    val dtoAltConstructorBuilder = FunSpec.constructorBuilder().addParameter(originalTypeParameter)
            .addKdoc(CodeBlock.builder()
                    .addStatement("Alternative constructor, used to map ")
                    .addStatement("from the given [%T] instance.", primaryTargetTypeElement.asKotlinTypeName()).build())
    val dtoAltConstructorCodeBuilder = CodeBlock.builder().addStatement("")

    // Create patch function
    val patchFunctionBuilder = getToPatchedFunctionBuilder(originalTypeParameter)
    val patchFunctionCodeBuilder = CodeBlock.builder().addStatement("val patched = %T(", primaryTargetTypeElement.asKotlinTypeName())

    // Create mapping function
    val toStateFunctionBuilder = getToTargetTypeFunctionBuilder()
    val toStateFunctionCodeBuilder = CodeBlock.builder()
            .addStatement("try {")
            .addStatement("   val originalTypeInstance = %T(", primaryTargetTypeElement.asKotlinTypeName())


    override fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, variableElement: VariableElement) {
        propertySpecBuilder.copyAnnotationsByBasePackage(variableElement, copyAnnotationPackages, FIELD)
    }

    override fun toPropertyName(variableElement: VariableElement): String =
            variableElement.simpleName.toString()

    override fun toPropertyTypeName(variableElement: VariableElement): TypeName =
            variableElement.asKotlinTypeName().copy(nullable = true)

    override fun toDefaultValueExpression(variableElement: VariableElement): String =
            variableElement.findAnnotationValue(DefaultValue::class.java, "value")?.value?.toString() ?: "null"

    override fun toMapStatement(variableElement: VariableElement, commaOrEmpty: String): Statement? {
        val propertyName = toPropertyName(variableElement)
        return Statement("      $propertyName = this.$propertyName${if (variableElement.isNullable()) "" else "!!"}$commaOrEmpty")
    }

    override fun toPatchStatement(variableElement: VariableElement, commaOrEmpty: String): Statement? {
        val propertyName = toPropertyName(variableElement)
        return Statement("      $propertyName = this.$propertyName ?: original.$propertyName$commaOrEmpty")
    }

    override fun toAltConstructorStatement(
            index: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName, commaOrEmpty: String
    ): Statement? {
        return Statement("      $propertyName = original.$propertyName$commaOrEmpty")
    }

    override fun processFields(
            typeSpecBuilder: TypeSpec.Builder,
            fields: List<VariableElement>) {
        fields.forEachIndexed { index, originalProperty ->
            val commaOrEmpty = if (index + 1 < fields.size) "," else ""
            // Tell KotlinPoet that the property is initialized via the constructor parameter,
            // by creating both a constructor param and member property
            val propertyName = toPropertyName(originalProperty)
            val propertyType = toPropertyTypeName(originalProperty)
            val propertyDefaultValue = toDefaultValueExpression(originalProperty)
            dtoConstructorBuilder.addParameter(ParameterSpec.builder(propertyName, propertyType)
                    .defaultValue(propertyDefaultValue)
                    .build())
            val propertySpecBuilder = toPropertySpecBuilder(index, originalProperty, propertyName, propertyType)
            addPropertyAnnotations(propertySpecBuilder, originalProperty)
            typeSpecBuilder.addProperty(propertySpecBuilder.build())
            // Add line to patch function
            patchFunctionCodeBuilder.addStatement(toPatchStatement(originalProperty, commaOrEmpty))
            // Add line to map function
            toStateFunctionCodeBuilder.addStatement(toMapStatement(originalProperty, commaOrEmpty))
            // Add line to alt constructor
            dtoAltConstructorCodeBuilder.addStatement(toAltConstructorStatement(index, originalProperty, propertyName, propertyType, commaOrEmpty))

            fieldProcessed(index, originalProperty, propertyName, propertyType)
        }
        finalize(typeSpecBuilder)
    }

    /** Override to add additional functionality to your [DtoMembersStrategy] implementation */
    override fun fieldProcessed(index: Int, originalProperty: VariableElement, propertyName: String, propertyType: TypeName) {
        // NO-OP
    }

    override fun toPropertySpecBuilder(
            index: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName
    ): PropertySpec.Builder = PropertySpec.builder(propertyName, propertyType)
                .mutable()
                .addModifiers(PUBLIC)
                .initializer(propertyName)

    override fun getToPatchedFunctionBuilder(
            originalTypeParameter: ParameterSpec
    ): FunSpec.Builder {
        val patchFunctionBuilder = FunSpec.builder("toPatched")
                .addModifiers(OVERRIDE)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create a patched copy of the given [%T] instance,", primaryTargetTypeElement.asKotlinTypeName())
                        .addStatement("updated using this DTO's non-null properties.").build())
                .addParameter(originalTypeParameter)
                .returns(primaryTargetTypeElement.asKotlinTypeName())
        return patchFunctionBuilder
    }

    override fun getToTargetTypeFunctionBuilder(): FunSpec.Builder {
        val toStateFunctionBuilder = FunSpec.builder("toTargetType")
                .addModifiers(OVERRIDE)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create an instance of [%T], using this DTO's properties.", primaryTargetTypeElement.asKotlinTypeName())
                        .addStatement("May throw a [DtoInsufficientStateMappingException] ")
                        .addStatement("if there is mot enough information to do so.").build())
                .returns(primaryTargetTypeElement.asKotlinTypeName())
        return toStateFunctionBuilder
    }

    protected open fun finalize(typeSpecBuilder: TypeSpec.Builder) {
        // Complete alt constructor
        dtoAltConstructorBuilder.callThisConstructor(dtoAltConstructorCodeBuilder.build())
        // Complete patch function
        patchFunctionCodeBuilder.addStatement(")")
        patchFunctionCodeBuilder.addStatement("return patched")
        // Complete mapping function
        toStateFunctionCodeBuilder.addStatement("   )")
        toStateFunctionCodeBuilder.addStatement("   return originalTypeInstance")
        toStateFunctionCodeBuilder.addStatement("}")
        toStateFunctionCodeBuilder.addStatement("catch(e: Exception) {")
        toStateFunctionCodeBuilder.addStatement("   throw %T(exception = e)", DtoInsufficientMappingException::class)
        toStateFunctionCodeBuilder.addStatement("}")

        // Add functions
        typeSpecBuilder
                .primaryConstructor(dtoConstructorBuilder.build())
                .addFunction(dtoAltConstructorBuilder.build())
                .addFunction(patchFunctionBuilder.addCode(patchFunctionCodeBuilder.build()).build())
                .addFunction(toStateFunctionBuilder.addCode(toStateFunctionCodeBuilder.build()).build())
    }
}
