package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manosbatsis.kotlin.utils.api.DefaultValue
import com.github.manosbatsis.kotlin.utils.api.DtoInsufficientMappingException
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.DtoMembersStrategy.Statement
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget.FIELD
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.asTypeName
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement

/**
 * Used optionally to implement delegates
 * for a subset of [DtoTypeStrategy]
 */
interface DtoMembersStrategy: ProcessingEnvironmentAware {
    data class Statement(val format: String, val args: Array<Any?> = emptyArray())


    val annotatedElementInfo: AnnotatedElementInfo
    val dtoNameStrategy: DtoNameStrategy
    val dtoTypeStrategy: DtoTypeStrategy

    override val processingEnvironment: ProcessingEnvironment
        get() = annotatedElementInfo.processingEnvironment

    /** Override to modify processing of individual fields */
    fun processFields(typeSpecBuilder: Builder, fields: List<VariableElement>)

    /**
     * Override to modify processing of DTO-specific fields,
     * e.g. from mixins
     */
    fun processDtoOnlyFields(
        typeSpecBuilder: TypeSpec.Builder,
        fields: List<VariableElement>
    )

    /** Override to change the property-level annotations applied   */
    fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, variableElement: VariableElement)
    fun getToPatchedFunctionBuilder(
        originalTypeParameter: ParameterSpec
    ): FunSpec.Builder

    fun getToTargetTypeFunctionBuilder(): FunSpec.Builder
    fun toPropertyName(variableElement: VariableElement): String
    fun toPropertyTypeName(variableElement: VariableElement): TypeName
    fun toDefaultValueExpression(variableElement: VariableElement): String
    fun toTargetTypeStatement(fieldIndex: Int, variableElement: VariableElement, commaOrEmpty: String): Statement?
    fun toPatchStatement(fieldIndex: Int, variableElement: VariableElement, commaOrEmpty: String): Statement?
    fun toAltConstructorStatement(fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName, commaOrEmpty: String): Statement?
    fun toPropertySpecBuilder(fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName): PropertySpec.Builder
    fun fieldProcessed(fieldIndex: Int, originalProperty: VariableElement, propertyName: String, propertyType: TypeName)
    fun getAltConstructorBuilder(): FunSpec.Builder
    fun getCompanionBuilder(): Builder
    fun getCreatorFunctionBuilder(originalTypeParameter: ParameterSpec): FunSpec.Builder
    fun toCreatorStatement(fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName, commaOrEmpty: String): Statement?
    fun addAltConstructor(typeSpecBuilder: Builder, dtoAltConstructorBuilder: FunSpec.Builder)
    fun finalize(typeSpecBuilder: Builder)
}


open class SimpleDtoMembersStrategy(
        override val annotatedElementInfo: AnnotatedElementInfo,
        override val dtoNameStrategy: DtoNameStrategy,
        override val dtoTypeStrategy: DtoTypeStrategy
) : DtoMembersStrategy, ProcessingEnvironmentAware, AnnotatedElementInfo by annotatedElementInfo {

    // Original type parameter, used in alt constructor and util functions
    val originalTypeParameter = ParameterSpec.builder("original", primaryTargetTypeElement.asKotlinTypeName()).build()

    // Create DTO primary constructor
    val dtoConstructorBuilder = FunSpec.constructorBuilder()

    val dtoAltConstructorCodeBuilder = CodeBlock.builder().addStatement("")

    // Create patch function
    val patchFunctionBuilder = getToPatchedFunctionBuilder(originalTypeParameter)
    val patchFunctionCodeBuilder = CodeBlock.builder().addStatement("val patched = %T(", primaryTargetTypeElement.asKotlinTypeName())

    // Create mapping function
    val targetTypeFunctionBuilder = getToTargetTypeFunctionBuilder()
    val targetTypeFunctionCodeBuilder = CodeBlock.builder()
            .addStatement("try {")
            .addStatement("   return %T(",
                    dtoTypeStrategy.getDtoTarget().asType().asTypeName())
    val companionObject = getCompanionBuilder()

    val creatorFunctionBuilder = getCreatorFunctionBuilder(originalTypeParameter)
    val creatorFunctionCodeBuilder = CodeBlock.builder()
            .addStatement("return %T(", dtoNameStrategy.getClassName())


    override fun getCreatorFunctionBuilder(
            originalTypeParameter: ParameterSpec
    ): FunSpec.Builder {
        val creatorFunctionBuilder = FunSpec.builder("mapToDto")
                .addModifiers(PUBLIC)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create a new DTO instance using the given [%T] as source.",
                                primaryTargetTypeElement.asType().asTypeName())
                        .build())
                .addParameter(originalTypeParameter)
                .returns(dtoNameStrategy.getClassName())
        return creatorFunctionBuilder
    }

    override fun getCompanionBuilder(): TypeSpec.Builder {
        return TypeSpec.companionObjectBuilder()
    }

    override fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, variableElement: VariableElement) {
        propertySpecBuilder.copyAnnotationsByBasePackage(variableElement, copyAnnotationPackages, FIELD)
    }

    override fun toPropertyName(variableElement: VariableElement): String =
            variableElement.simpleName.toString()

    override fun toPropertyTypeName(variableElement: VariableElement): TypeName =
            variableElement.asKotlinTypeName().copy(nullable = true)

    override fun toDefaultValueExpression(variableElement: VariableElement): String {
        val mixinVariableElement = annotatedElementInfo
                .mixinTypeElementFields
                .find { it.simpleName == variableElement.simpleName }

        return listOfNotNull(mixinVariableElement, variableElement)
                .mapNotNull { findDefaultValueAnnotationValue(it) }
                .firstOrNull() ?: "null"
    }

    fun findDefaultValueAnnotationValue(variableElement: VariableElement): String? =
            variableElement.findAnnotationValue(DefaultValue::class.java, "value")?.value?.toString() ?: null

    override fun toTargetTypeStatement(fieldIndex: Int, variableElement: VariableElement, commaOrEmpty: String): Statement? {
        val propertyName = toPropertyName(variableElement)
        return Statement("      $propertyName = this.$propertyName${if (variableElement.isNullable()) "" else "!!"}$commaOrEmpty")
    }

    override fun toPatchStatement(fieldIndex: Int, variableElement: VariableElement, commaOrEmpty: String): Statement? {
        val propertyName = toPropertyName(variableElement)
        return Statement("      $propertyName = this.$propertyName ?: original.$propertyName$commaOrEmpty")
    }

    override fun toAltConstructorStatement(
            fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName, commaOrEmpty: String
    ): Statement? {
        return Statement("      $propertyName = original.$propertyName$commaOrEmpty")
    }

    override fun toCreatorStatement(
        fieldIndex: Int,
        variableElement: VariableElement,
        propertyName: String,
        propertyType: TypeName,
        commaOrEmpty: String
    ): Statement? {
        return Statement("      $propertyName = original.$propertyName$commaOrEmpty")
    }

    override fun processDtoOnlyFields(
        typeSpecBuilder: TypeSpec.Builder,
        fields: List<VariableElement>
    ) {
        fields.forEachIndexed { fieldIndex, originalProperty ->
            val (propertyName, propertyType) =
                addProperty(originalProperty, fieldIndex, typeSpecBuilder)
            fieldProcessed(fieldIndex, originalProperty, propertyName, propertyType)
        }
    }

    override fun processFields(
        typeSpecBuilder: TypeSpec.Builder,
        fields: List<VariableElement>
    ) {
        fields.forEachIndexed { fieldIndex, originalProperty ->
            val commaOrEmpty = if (fieldIndex + 1 < fields.size) "," else ""
            // Tell KotlinPoet that the property is initialized via the constructor parameter,
            // by creating both a constructor param and member property
            val (propertyName, propertyType) = addProperty(originalProperty, fieldIndex, typeSpecBuilder)
            // TODO: just separate and decouple the darn component builders already
            // Add line to patch function
            patchFunctionCodeBuilder.addStatement(toPatchStatement(fieldIndex, originalProperty, commaOrEmpty))
            // Add line to map function
            targetTypeFunctionCodeBuilder.addStatement(
                toTargetTypeStatement(
                    fieldIndex,
                    originalProperty,
                    commaOrEmpty
                )
            )
            // Add line to alt constructor
            dtoAltConstructorCodeBuilder.addStatement(
                toAltConstructorStatement(
                    fieldIndex,
                    originalProperty,
                    propertyName,
                    propertyType,
                    commaOrEmpty
                )
            )
            // Add line to create
            creatorFunctionCodeBuilder.addStatement(
                toCreatorStatement(
                    fieldIndex,
                    originalProperty,
                    propertyName,
                    propertyType,
                    commaOrEmpty
                )
            )
            //
            fieldProcessed(fieldIndex, originalProperty, propertyName, propertyType)
        }
    }

    protected fun addProperty(
        originalProperty: VariableElement,
        fieldIndex: Int,
        typeSpecBuilder: Builder
    ): Pair<String, TypeName> {
        val propertyName = toPropertyName(originalProperty)
        val propertyType = toPropertyTypeName(originalProperty)
        val propertyDefaultValue = toDefaultValueExpression(originalProperty)
        dtoConstructorBuilder.addParameter(
            ParameterSpec.builder(propertyName, propertyType)
                .defaultValue(propertyDefaultValue)
                .build()
        )
        val propertySpecBuilder = toPropertySpecBuilder(fieldIndex, originalProperty, propertyName, propertyType)
        addPropertyAnnotations(propertySpecBuilder, originalProperty)
        typeSpecBuilder.addProperty(propertySpecBuilder.build())
        return Pair(propertyName, propertyType)
    }

    /** Override to add additional functionality to your [DtoMembersStrategy] implementation */
    override fun fieldProcessed(
        fieldIndex: Int,
        originalProperty: VariableElement,
        propertyName: String,
        propertyType: TypeName
    ) {
        // NO-OP
    }

    override fun toPropertySpecBuilder(
        fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName
    ): PropertySpec.Builder = PropertySpec.builder(propertyName, propertyType)
        .mutable()
                .addModifiers(PUBLIC)
                .initializer(propertyName)


    // Create DTO alternative constructor
    override fun getAltConstructorBuilder() = FunSpec.constructorBuilder().addParameter(originalTypeParameter)
            .addKdoc(CodeBlock.builder()
                    .addStatement("Alternative constructor, used to map ")
                    .addStatement("from the given [%T] instance.", primaryTargetTypeElement.asKotlinTypeName()).build())

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

    override fun finalize(typeSpecBuilder: TypeSpec.Builder) {
        // Complete alt constructor
        val dtoAltConstructorBuilder = getAltConstructorBuilder()
            .callThisConstructor(dtoAltConstructorCodeBuilder.build())
        addAltConstructor(typeSpecBuilder, dtoAltConstructorBuilder)
        // Complete creator function

        // Complete creator function
        creatorFunctionCodeBuilder.addStatement(")")
        //creatorFunctionCodeBuilder.addStatement("return dto")
        // Complete patch function
        patchFunctionCodeBuilder.addStatement(")")
        patchFunctionCodeBuilder.addStatement("return patched")
        // Complete mapping function
        targetTypeFunctionCodeBuilder.addStatement("   )")
        targetTypeFunctionCodeBuilder.addStatement("}")
        targetTypeFunctionCodeBuilder.addStatement("catch(e: Exception) {")
        targetTypeFunctionCodeBuilder.addStatement("   throw %T(exception = e)", DtoInsufficientMappingException::class)
        targetTypeFunctionCodeBuilder.addStatement("}")
        // Add functions
        typeSpecBuilder
                .primaryConstructor(dtoConstructorBuilder.build())
                .addType(companionObject.addFunction(
                        creatorFunctionBuilder
                                .addStatement(creatorFunctionCodeBuilder.build().toString())
                                .build()
                ).build())
                .addFunction(patchFunctionBuilder.addCode(patchFunctionCodeBuilder.build()).build())
                .addFunction(targetTypeFunctionBuilder.addCode(targetTypeFunctionCodeBuilder.build()).build())
    }

    override fun addAltConstructor(typeSpecBuilder: Builder, dtoAltConstructorBuilder: FunSpec.Builder) {
        typeSpecBuilder.addFunction(dtoAltConstructorBuilder.build())
    }
}
