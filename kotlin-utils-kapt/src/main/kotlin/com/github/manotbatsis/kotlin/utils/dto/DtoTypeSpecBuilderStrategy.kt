package com.github.manotbatsis.kotlin.utils.dto

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manosbatsis.kotlin.utils.api.DefaultValue
import com.github.manosbatsis.kotlin.utils.api.Dto
import com.github.manosbatsis.kotlin.utils.api.DtoInsufficientMappingException
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget.FIELD
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.DATA
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.asClassName
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement

internal interface DtoTypeSpecBuilderStrategy : ProcessingEnvironmentAware {

    companion object {}
    /** The [DtoTypeSpecBuilder] that created ths instance */
    val dtoTypeSpecBuilder: DtoTypeSpecBuilder

    /**
     * Override to change how the [TypeSpec.Builder] is generated for this DTO,
     * when overriding other methods is not adequate
     */
    fun dtoTypeSpecBuilder(): TypeSpec.Builder

    /**
     * Override to change how the [TypeSpec] is generated for this DTO,
     * when overriding other methods is not adequate
     */
    fun dtoTypeSpec(): TypeSpec

    /** Override to change the type-level annotations applied to the DTO  */
    fun addAnnotations(typeSpecBuilder: Builder)

    /** Override to change the property-level annotations applied   */
    fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, variableElement: VariableElement)

    /** Override to change the type-level KDoc applied to the DTO  */
    fun addKdoc(typeSpecBuilder: Builder)

    /** Override to change the type-level [KModifier]s applied to the DTO  */
    fun addModifiers(typeSpecBuilder: Builder)

    /** Override to change the super types the DTO extends or implements  */
    fun addSuperTypes(typeSpecBuilder: Builder)

    /** Override to change the DTO package and class name */
    fun getClassName(): ClassName

    /** Process original type fields and add DTO members */
    fun addMembers(typeSpecBuilder: Builder)

    /** Override to modify the fields to process, i.e. replicate for the DTO */
    fun getFieldsToProcess(): List<VariableElement>
}

open class DefaultDtoTypeSpecBuilderStrategy(
        override val processingEnvironment: ProcessingEnvironment,
        override val dtoTypeSpecBuilder: DtoTypeSpecBuilder
) : DtoTypeSpecBuilderStrategy {

    override fun dtoTypeSpec(): TypeSpec = dtoTypeSpecBuilder().build()

    override fun dtoTypeSpecBuilder(): TypeSpec.Builder {
        val dtoTypeSpecBuilder = TypeSpec.classBuilder(getClassName())
        addSuperTypes(dtoTypeSpecBuilder)
        addModifiers(dtoTypeSpecBuilder)
        addKdoc(dtoTypeSpecBuilder)
        addAnnotations(dtoTypeSpecBuilder)
        addMembers(dtoTypeSpecBuilder)
        return dtoTypeSpecBuilder
    }

    override fun addAnnotations(typeSpecBuilder: Builder) {
        typeSpecBuilder.copyAnnotationsByBasePackage(dtoTypeSpecBuilder.originalTypeElement, dtoTypeSpecBuilder.copyAnnotationPackages)
    }

    override fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, variableElement: VariableElement) {
        propertySpecBuilder.copyAnnotationsByBasePackage(variableElement, dtoTypeSpecBuilder.copyAnnotationPackages, FIELD)
    }

    override fun addKdoc(typeSpecBuilder: Builder) {
        typeSpecBuilder.addKdoc("A [%T]-specific [%T] implementation", dtoTypeSpecBuilder.originalTypeName, Dto::class)
    }

    override fun addModifiers(typeSpecBuilder: Builder) {
        typeSpecBuilder.addModifiers(DATA)
    }

    override fun addSuperTypes(typeSpecBuilder: Builder) {
        typeSpecBuilder.addSuperinterface(Dto::class.asClassName().parameterizedBy(dtoTypeSpecBuilder.originalTypeName))
    }

    override fun getClassName(): ClassName =
            ClassName(dtoTypeSpecBuilder.targetPackage, "${dtoTypeSpecBuilder.originalTypeElement.simpleName}Dto")

    override fun addMembers(typeSpecBuilder: Builder) {
        // Original type parameter, used in alt constructor and util functions
        val originalTypeParameter = ParameterSpec.builder("original", dtoTypeSpecBuilder.originalTypeName).build()
        // Create DTO primary constructor
        val dtoConstructorBuilder = FunSpec.constructorBuilder()
        // Create DTO alternative constructor
        val dtoAltConstructorBuilder = FunSpec.constructorBuilder().addParameter(originalTypeParameter)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Alternative constructor, used to map ")
                        .addStatement("from the given [%T] instance.", dtoTypeSpecBuilder.originalTypeName).build())
        val dtoAltConstructorCodeBuilder = CodeBlock.builder().addStatement("")
        // Create patch function
        val patchFunctionBuilder = FunSpec.builder("toPatched")
                .addModifiers(OVERRIDE)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create a patched copy of the given [%T] instance,", dtoTypeSpecBuilder.originalTypeName)
                        .addStatement("updated using this DTO's non-null properties.").build())
                .addParameter(originalTypeParameter)
                .returns(dtoTypeSpecBuilder.originalTypeName)
        val patchFunctionCodeBuilder = CodeBlock.builder().addStatement("val patched = %T(", dtoTypeSpecBuilder.originalTypeName)
        // Create mapping function
        val toStateFunctionBuilder = FunSpec.builder("toTargetType")
                .addModifiers(OVERRIDE)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create an instance of [%T], using this DTO's properties.", dtoTypeSpecBuilder.originalTypeName)
                        .addStatement("May throw a [DtoInsufficientStateMappingException] ")
                        .addStatement("if there is mot enough information to do so.").build())
                .returns(dtoTypeSpecBuilder.originalTypeName)
        val toStateFunctionCodeBuilder = CodeBlock.builder()
                .addStatement("try {")
                .addStatement("   val originalTypeInstance = %T(", dtoTypeSpecBuilder.originalTypeName)
        val fieldsToProcess = getFieldsToProcess()
        fieldsToProcess.forEachIndexed { index, originalVariableelement ->
            val commaOrEmpty = if (index + 1 < fieldsToProcess.size) "," else ""
            // Tell KotlinPoet that the property is initialized via the constructor parameter,
            // by creating both a constructor param and member property
            val propertyName = originalVariableelement.simpleName.toString()
            val propertyType = originalVariableelement.asKotlinTypeName().copy(nullable = true)
            val propertyDefaultValue = originalVariableelement
                    .findAnnotationValue(DefaultValue::class.java, "value")?.value?.toString() ?: "null"
            dtoConstructorBuilder.addParameter(ParameterSpec.builder(propertyName, propertyType)
                    .defaultValue(propertyDefaultValue)
                    .build())
            val propertySpecBuilder = PropertySpec.builder(propertyName, propertyType)
                    .mutable()
                    .addModifiers(PUBLIC)
                    .initializer(propertyName)
            addPropertyAnnotations(propertySpecBuilder, originalVariableelement)
            typeSpecBuilder.addProperty(propertySpecBuilder.build())
            // Add line to path function
            patchFunctionCodeBuilder.addStatement("      $propertyName = this.$propertyName ?: original.$propertyName$commaOrEmpty")
            // Add line to map function
            val nullableOrNot = if (originalVariableelement.isNullable()) "" else "!!"
            toStateFunctionCodeBuilder.addStatement("      $propertyName = this.$propertyName$nullableOrNot$commaOrEmpty")
            // Add line to alt constructor
            dtoAltConstructorCodeBuilder.addStatement("      $propertyName = original.$propertyName$commaOrEmpty")
        }

        // Complete alt constructor
        dtoAltConstructorBuilder.callThisConstructor(dtoAltConstructorCodeBuilder.build())
        // Complete patch function
        patchFunctionCodeBuilder.addStatement(")")
        patchFunctionCodeBuilder.addStatement("return patched")
        // Complete mappiong function

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

    override fun getFieldsToProcess(): List<VariableElement> =
            if (dtoTypeSpecBuilder.fields.isNotEmpty()) dtoTypeSpecBuilder.fields
            else dtoTypeSpecBuilder.originalTypeElement.accessibleConstructorParameterFields()


}