package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manosbatsis.kotlin.utils.api.DefaultValue
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement

/** Simple implementation of [DtoMembersStrategy] */
open class SimpleDtoMembersStrategy(
        override val annotatedElementInfo: AnnotatedElementInfo,
        override val dtoNameStrategy: DtoNameStrategy,
        override val dtoTypeStrategy: DtoTypeStrategy
) : DtoMembersStrategy, ProcessingEnvironmentAware, AnnotatedElementInfo by annotatedElementInfo {

    /** Alt constructor using a "root" strategy  */
    constructor(
            rootDtoStrategy: DtoStrategyLesserComposition
    ) : this(rootDtoStrategy.annotatedElementInfo, rootDtoStrategy, rootDtoStrategy) {
        this.rootDtoStrategy = rootDtoStrategy
    }

    var rootDtoStrategy: DtoStrategyLesserComposition? = null

    val rootDtoMembersStrategy: DtoMembersStrategy by lazy {
        if (rootDtoStrategy != null && rootDtoStrategy is DtoMembersStrategy) rootDtoStrategy as DtoMembersStrategy else this
    }

    // Original type parameter, used in alt constructor and util functions
    val originalTypeParameter by lazy { ParameterSpec.builder("original", dtoTypeStrategy.getDtoTarget()).build() }

    // Create DTO primary constructor
    val dtoConstructorBuilder = FunSpec.constructorBuilder()

    val dtoAltConstructorCodeBuilder = CodeBlock.builder().addStatement("")

    // Create patch function
    val patchFunctionBuilder by lazy { rootDtoMembersStrategy.getToPatchedFunctionBuilder(originalTypeParameter) }
    val patchFunctionCodeBuilder by lazy {
        if (annotatedElementInfo.isNonDataClass)
            CodeBlock.builder().addStatement("val patched = %T(", dtoTypeStrategy.getDtoTarget())
        else
            CodeBlock.builder().addStatement("val patched = original.copy(")
    }


    // Create mapping function
    val targetTypeFunctionBuilder by lazy { rootDtoMembersStrategy.getToTargetTypeFunctionBuilder() }
    val targetTypeFunctionCodeBuilder by lazy {

        val useTargetTypeName = annotatedElementInfo.toTargetTypeFunctionConfig.targetTypeNameOverride
                ?: dtoTypeStrategy.getDtoTarget()
        if (annotatedElementInfo.toTargetTypeFunctionConfig.skip)
            CodeBlock.builder().addStatement("TODO(\"Not yet implemented\")")
        else
            CodeBlock.builder().addStatement("   return %T(", useTargetTypeName)
    }
    val companionObject by lazy { rootDtoMembersStrategy.getCompanionBuilder() }

    val creatorFunctionBuilder by lazy { rootDtoMembersStrategy.getCreatorFunctionBuilder(originalTypeParameter) }
    val creatorFunctionCodeBuilder by lazy {
        CodeBlock.builder().addStatement("return ${dtoNameStrategy.getClassName().simpleName}(")
    }


    protected fun assignmentCtxForToTargetType(propertyName: String): AssignmentContext =
            AssignmentContext.OUT.withFallbackValue("?: errNull(\"$propertyName\")")

    protected fun assignmentCtxForToPatched(propertyName: String): AssignmentContext =
            AssignmentContext.OUT.withFallbackValue(" ?: original.$propertyName")


    protected fun assignmentCtxForToAltConstructor(propertyName: String): AssignmentContext =
            AssignmentContext.IN.withFallbackValue("?: %T.errNull(\"$propertyName\")")
                    .withFallbackArg(getRootDtoTypeFromRootStrategy())

    protected fun assignmentCtxForOwnCreator(propertyName: String): AssignmentContext =
            AssignmentContext.IN.withFallbackValue("?: %T.errNull(\"$propertyName\")")
                    .withFallbackArg(getRootDtoTypeFromRootStrategy())

    protected fun getRootDtoTypeFromRootStrategy(): TypeName =
            rootDtoMembersStrategy.dtoTypeStrategy.getRootDtoType()

    override fun getCreatorFunctionBuilder(
            originalTypeParameter: ParameterSpec
    ): FunSpec.Builder {
        val creatorFunctionBuilder = FunSpec.builder("from")
                .addModifiers(KModifier.PUBLIC)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create a new instance using the given [%T] as source.",
                                dtoTypeStrategy.getDtoTarget())
                        .build())
                .addParameter(originalTypeParameter)
                .returns(dtoNameStrategy.getClassName())
        return creatorFunctionBuilder
    }

    override fun getCompanionBuilder(): TypeSpec.Builder {
        return TypeSpec.companionObjectBuilder()
    }

    override fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, variableElement: VariableElement) {
        propertySpecBuilder.copyAnnotationsByBasePackage(variableElement, copyAnnotationPackages, AnnotationSpec.UseSiteTarget.FIELD)
    }

    override fun toPropertyName(variableElement: VariableElement): String =
            variableElement.simpleName.toString()

    override fun toPropertyTypeName(variableElement: VariableElement): TypeName =
            variableElement.asKotlinTypeName().copy(nullable = true)

    override fun toDefaultValueExpression(variableElement: VariableElement): String? {
        val mixinVariableElement = annotatedElementInfo.mixinTypeElementFields
                .find { it.simpleName == variableElement.simpleName }

        return listOfNotNull(mixinVariableElement, variableElement)
                .mapNotNull { rootDtoMembersStrategy.findDefaultValueAnnotationValue(it) }
                .firstOrNull()
        // Null if nullable, no default value otherwise
                ?: if (rootDtoMembersStrategy.toPropertyTypeName(variableElement).isNullable) "null" else null
    }

    override fun findDefaultValueAnnotationValue(variableElement: VariableElement): String? =
            variableElement.findAnnotationValue(DefaultValue::class.java, "value")?.value?.toString()


    override fun toTargetTypeStatement(fieldIndex: Int, variableElement: VariableElement, commaOrEmpty: String): DtoMembersStrategy.Statement? {
        val propertyName = rootDtoMembersStrategy.toPropertyName(variableElement)
        val assignmentContext = assignmentCtxForToTargetType(propertyName)
        val maybeNullFallback = maybeCheckForNull(variableElement, assignmentContext)
        return DtoMembersStrategy.Statement("      $propertyName = this.$propertyName$maybeNullFallback$commaOrEmpty", assignmentContext.fallbackArgs)
    }

    override fun maybeCheckForNull(
            variableElement: VariableElement,
            assignmentContext: AssignmentContext
    ): String {
        val isSourceNotNull = rootDtoMembersStrategy.isNonNull(variableElement, assignmentContext.source)
        val isTargetNullable = rootDtoMembersStrategy.isNullable(variableElement, assignmentContext.target)
        return if (isSourceNotNull || isTargetNullable) ""
        else "${assignmentContext.fallbackValue}"
    }

    override fun isNullable(
            variableElement: VariableElement, fieldContext: FieldContext
    ): Boolean = when (fieldContext) {
        FieldContext.GENERATED_TYPE -> true//rootDtoMembersStrategy.toPropertyTypeName(variableElement).isNullable
        FieldContext.TARGET_TYPE -> variableElement.isNullable()
        FieldContext.MIXIN_TYPE -> true
    }


    override fun toPatchStatement(fieldIndex: Int, variableElement: VariableElement, commaOrEmpty: String): DtoMembersStrategy.Statement? {
        val propertyName = rootDtoMembersStrategy.toPropertyName(variableElement)
        val assignmentContext = assignmentCtxForToPatched(propertyName)
        val maybeNullFallback = maybeCheckForNull(variableElement, assignmentContext)
        return DtoMembersStrategy.Statement("      $propertyName = this.$propertyName$maybeNullFallback$commaOrEmpty")
    }


    override fun toAltConstructorStatement(
            fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName, commaOrEmpty: String
    ): DtoMembersStrategy.Statement? {
        val assignmentContext = assignmentCtxForToAltConstructor(propertyName)
        val maybeNullFallback = maybeCheckForNull(variableElement, assignmentContext)
        return DtoMembersStrategy.Statement("      $propertyName = original.$propertyName$maybeNullFallback$commaOrEmpty")
    }

    override fun toCreatorStatement(
            fieldIndex: Int,
            variableElement: VariableElement,
            propertyName: String,
            propertyType: TypeName,
            commaOrEmpty: String
    ): DtoMembersStrategy.Statement? {
        val assignmentContext = assignmentCtxForOwnCreator(propertyName)
        val maybeNullFallback = maybeCheckForNull(variableElement, assignmentContext)
        return DtoMembersStrategy.Statement("      $propertyName = original.$propertyName$maybeNullFallback$commaOrEmpty")
    }

    override fun processDtoOnlyFields(
            typeSpecBuilder: TypeSpec.Builder,
            fields: List<VariableElement>
    ) {
        fields.forEachIndexed { fieldIndex, originalProperty ->
            val (propertyName, propertyType) =
                    rootDtoMembersStrategy.addProperty(originalProperty, fieldIndex, typeSpecBuilder)
            rootDtoMembersStrategy.fieldProcessed(fieldIndex, originalProperty, propertyName, propertyType)
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
            val (propertyName, propertyType) = rootDtoMembersStrategy.addProperty(originalProperty, fieldIndex, typeSpecBuilder)
            // TODO: just separate and decouple the darn component builders already
            // Add line to patch function
            patchFunctionCodeBuilder.addStatement(rootDtoMembersStrategy.toPatchStatement(fieldIndex, originalProperty, commaOrEmpty))
            // Add line to map function
            targetTypeFunctionCodeBuilder.addStatement(
                    rootDtoMembersStrategy.toTargetTypeStatement(
                            fieldIndex,
                            originalProperty,
                            commaOrEmpty))
            // Add line to alt constructor
            dtoAltConstructorCodeBuilder.addStatement(
                    rootDtoMembersStrategy.toAltConstructorStatement(
                            fieldIndex,
                            originalProperty,
                            propertyName,
                            propertyType,
                            commaOrEmpty
                    )
            )
            // Add line to create
            creatorFunctionCodeBuilder.addStatement(
                    rootDtoMembersStrategy.toCreatorStatement(
                            fieldIndex,
                            originalProperty,
                            propertyName,
                            propertyType,
                            commaOrEmpty
                    )
            )
            //
            rootDtoMembersStrategy.fieldProcessed(fieldIndex, originalProperty, propertyName, propertyType)
        }
    }

    override fun addProperty(
            originalProperty: VariableElement,
            fieldIndex: Int,
            typeSpecBuilder: TypeSpec.Builder
    ): Pair<String, TypeName> {
        val propertyName = rootDtoMembersStrategy.toPropertyName(originalProperty)
        val propertyType = rootDtoMembersStrategy.toPropertyTypeName(originalProperty)
        val propertyDefaultValue = rootDtoMembersStrategy.toDefaultValueExpression(originalProperty)
        dtoConstructorBuilder.addParameter(
                ParameterSpec.builder(propertyName, propertyType)
                        .apply { propertyDefaultValue?.let { defaultValue(it) } }.build()
        )
        val propertySpecBuilder = rootDtoMembersStrategy.toPropertySpecBuilder(fieldIndex, originalProperty, propertyName, propertyType)
        rootDtoMembersStrategy.addPropertyAnnotations(propertySpecBuilder, originalProperty)
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
            .addModifiers(KModifier.PUBLIC)
                .initializer(propertyName)


    // Create DTO alternative constructor
    override fun getAltConstructorBuilder() = FunSpec.constructorBuilder().addParameter(originalTypeParameter)
            .addKdoc(CodeBlock.builder()
                    .addStatement("Alternative constructor, used to map ")
                    .addStatement("from the given [%T] instance.", dtoTypeStrategy.getDtoTarget()).build())

    override fun getToPatchedFunctionBuilder(
            originalTypeParameter: ParameterSpec
    ): FunSpec.Builder {
        val patchFunctionBuilder = FunSpec.builder("toPatched")
                .addModifiers(KModifier.OVERRIDE)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create a patched copy of the given [%T] instance,", dtoTypeStrategy.getDtoTarget())
                        .addStatement("updated using this DTO's non-null properties.").build())
                .addParameter(originalTypeParameter)
                .returns(dtoTypeStrategy.getDtoTarget())
        return patchFunctionBuilder
    }

    override fun getToTargetTypeFunctionBuilder(): FunSpec.Builder {

        with(annotatedElementInfo.toTargetTypeFunctionConfig) {
            val useTargetTypeName = targetTypeNameOverride ?: dtoTypeStrategy.getDtoTarget()
            val toStateFunctionBuilder = FunSpec.builder("toTargetType")
                    .addModifiers(KModifier.OVERRIDE)
                    .addKdoc(if (skip)
                        CodeBlock.builder().addStatement("Not yet implemented").build()
                    else CodeBlock.builder()
                            .addStatement("Create an instance of [%T], using this DTO's properties.", useTargetTypeName)
                            .addStatement("May throw a [DtoInsufficientStateMappingException] ")
                            .addStatement("if there is mot enough information to do so.").build())
                    .returns(useTargetTypeName)
            params.forEach { toStateFunctionBuilder.addParameter(it) }
            return toStateFunctionBuilder
        }
    }

    override fun finalize(typeSpecBuilder: TypeSpec.Builder) {
        // Complete alt constructor
        val dtoAltConstructorBuilder = rootDtoMembersStrategy.getAltConstructorBuilder()
                .callThisConstructor(dtoAltConstructorCodeBuilder.build())
        rootDtoMembersStrategy.addAltConstructor(typeSpecBuilder, dtoAltConstructorBuilder)
        // Complete creator function

        // Complete creator function
        creatorFunctionCodeBuilder.addStatement(")")
        //creatorFunctionCodeBuilder.addStatement("return dto")
        // Complete patch function
        patchFunctionCodeBuilder.addStatement(")")
        patchFunctionCodeBuilder.addStatement("return patched")
        // Complete mapping function
        if (!annotatedElementInfo.toTargetTypeFunctionConfig.skip)
            targetTypeFunctionCodeBuilder.addStatement("   )")
        // Add functions
        typeSpecBuilder
                .primaryConstructor(dtoConstructorBuilder.build())
                .addType(companionObject.addFunction(
                        creatorFunctionBuilder
                                .addStatement(creatorFunctionCodeBuilder.build().toString())
                                .build()
                ).build())
        typeSpecBuilder.addFunction(patchFunctionBuilder.addCode(patchFunctionCodeBuilder.build()).build())
        typeSpecBuilder.addFunction(targetTypeFunctionBuilder.addCode(targetTypeFunctionCodeBuilder.build()).build())
    }

    override fun addAltConstructor(typeSpecBuilder: TypeSpec.Builder, dtoAltConstructorBuilder: FunSpec.Builder) {
        typeSpecBuilder.addFunction(dtoAltConstructorBuilder.build())
    }

    override val processingEnvironment: ProcessingEnvironment
        get() = annotatedElementInfo.processingEnvironment
}