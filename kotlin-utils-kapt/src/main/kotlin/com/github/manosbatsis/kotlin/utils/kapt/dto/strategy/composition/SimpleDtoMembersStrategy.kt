package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manosbatsis.kotlin.utils.api.DefaultValue
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.AssignmentContext
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.FieldContext
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementFieldInfo
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.*
import javax.annotation.processing.ProcessingEnvironment

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
        val instanceInitBlock = if (annotatedElementInfo.isNonDataClass)
            CodeBlock.builder().addStatement("val patched = %T(", dtoTypeStrategy.getDtoTarget())
        else
            CodeBlock.builder().addStatement("val patched = original.copy(")
        val instanceInitClosingBlock = CodeBlock.builder().addStatement(")")
        val returnBlock = CodeBlock.builder().addStatement("return patched")
        val instanceAltInitBlock = CodeBlock.builder().addStatement("val patched = original")
        CreateOrUpdateInstanceFunctionBuilder(
            instanceInitBlock = instanceInitBlock,
            instanceInitClosingBlock = instanceInitClosingBlock,
            returnBlock = returnBlock,
            instanceAltInitBlock = instanceAltInitBlock)
    }

    class CreateOrUpdateInstanceFunctionBuilder(
        /** Target instance initialization using e.g. constructor or data class copy() */
        val instanceInitBlock: CodeBlock.Builder,
        /** Target instance initialization closing, typically a closing parenthesis */
        val instanceInitClosingBlock: CodeBlock.Builder,
        /** Return block */
        val returnBlock: CodeBlock.Builder,
        /** Used instead of the [instanceInitBlock] when [instanceInitArgsBlock] is empty */
        val instanceAltInitBlock: CodeBlock.Builder? = null
    ){
        /** Stores statements used for constructor/copy parameters */
        val instanceInitArgsBlock: CodeBlock.Builder = CodeBlock.builder()
        /** Stores statements used for member mutations */
        val instanceMutationsBlock: CodeBlock.Builder = CodeBlock.builder()

        fun toCodeBlock(): CodeBlock {
            val useAltInit = instanceInitArgsBlock.isEmpty() && instanceAltInitBlock != null
            val code = CodeBlock.builder()
            if(!useAltInit){
                code.add(instanceInitBlock.build())
                if(instanceInitArgsBlock.isNotEmpty())
                    code.indent().add(instanceInitArgsBlock.build()).unindent()
                code.add(instanceInitClosingBlock.build())
            } else code.add(instanceAltInitBlock!!.build())

            if(instanceMutationsBlock.isNotEmpty()) code
                .add(instanceMutationsBlock.build())
            if(returnBlock.isNotEmpty()) code.add(returnBlock.build())
            return code.build()
        }
    }

    // Create mapping function
    val targetTypeFunctionBuilder by lazy { rootDtoMembersStrategy.getToTargetTypeFunctionBuilder() }
    val targetTypeFunctionCodeBuilder by lazy {

        val useTargetTypeName = annotatedElementInfo.toTargetTypeFunctionConfig.targetTypeNameOverride
                ?: dtoTypeStrategy.getDtoTarget()
        val instanceInitBlock = if (annotatedElementInfo.toTargetTypeFunctionConfig.skip)
            CodeBlock.builder().addStatement("TODO(\"Not yet implemented\")")
        else
            CodeBlock.builder().addStatement("val instance = %T(", useTargetTypeName)

        val instanceInitClosingBlock = if (annotatedElementInfo.toTargetTypeFunctionConfig.skip)
            CodeBlock.builder()
        else
            CodeBlock.builder().addStatement(")")

        val returnBlock = CodeBlock.builder().addStatement("return instance")

        CreateOrUpdateInstanceFunctionBuilder(
            instanceInitBlock = instanceInitBlock,
            instanceInitClosingBlock = instanceInitClosingBlock,
            returnBlock = returnBlock)
    }

    val companionObject by lazy { rootDtoMembersStrategy.getCompanionBuilder() }

    val creatorFunctionBuilder by lazy { rootDtoMembersStrategy.getCreatorFunctionBuilder(originalTypeParameter) }
    val creatorFunctionCodeBuilder by lazy {
        CodeBlock.builder().addStatement("return ${dtoNameStrategy.getClassName().simpleName}(")
    }

    protected fun maybeToMutableCollectionSuffix(
        fieldInfo: AnnotatedElementFieldInfo
    ): String {
        return if(useMutableIterables() && fieldInfo.variableElement.isIterable())
            "${if(toPropertyTypeName(fieldInfo).isNullable) "?." else "."}toMutable${fieldInfo.variableElement.asType().asTypeElement().simpleName}()"
        else ""
    }

    protected fun assignmentCtxForToTargetType(
        fromTypeName: TypeName,
        tofieldInfo: AnnotatedElementFieldInfo
    ): AssignmentContext{
        val toTypeName = tofieldInfo.variableElement.asKotlinTypeName()
        return if(fromTypeName.isNullable && !toTypeName.isNullable)
            AssignmentContext.OUT.withFallbackValue(" ?: errNull(\"${tofieldInfo.variableElement.simpleName}\")")
        else
            AssignmentContext.OUT.withFallbackValue("")
    }

    @Deprecated("use assignmentCtxForToTargetType(TypeName, VariableElement)")
    protected fun assignmentCtxForToTargetType(propertyName: String): AssignmentContext {
        return AssignmentContext.OUT.withFallbackValue(" ?: errNull(\"$propertyName\")")
    }


    protected fun assignmentCtxForToAltConstructor(propertyName: String): AssignmentContext =
            AssignmentContext.IN.withFallbackValue(" ?: %T.errNull(\"$propertyName\")")
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

    override fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, fieldInfo: AnnotatedElementFieldInfo) {
        propertySpecBuilder.copyAnnotationsByBasePackage(fieldInfo.variableElement, copyAnnotationPackages, AnnotationSpec.UseSiteTarget.FIELD)
    }

    override fun toPropertyName(fieldInfo: AnnotatedElementFieldInfo): String =
        fieldInfo.variableElement.simpleName.toString()

    override fun toPropertyTypeName(fieldInfo: AnnotatedElementFieldInfo): TypeName =
        fieldInfo.variableElement.asKotlinTypeName(useMutableIterables()).copy(nullable = defaultNullable())

    override fun toDefaultValueExpression(fieldInfo: AnnotatedElementFieldInfo): Pair<String, Boolean>? {
        val mixinVariableElement = annotatedElementInfo.mixinTypeElementFields
                .find { it.variableElement.simpleName == fieldInfo.variableElement.simpleName }

        return listOfNotNull(mixinVariableElement, fieldInfo)
                .mapNotNull { rootDtoMembersStrategy.findDefaultValueAnnotationValue(it, annotatedElementInfo) }
                .firstOrNull()
                // Null if nullable, no default value otherwise
                ?: if (rootDtoMembersStrategy.toPropertyTypeName(fieldInfo).isNullable) Pair("null", true) else null
    }

    override fun findDefaultValueAnnotationValue(
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo
    ): Pair<String, Boolean>? = fieldInfo.variableElement.annotationMirrors
            .find { it.annotationType.asElement().simpleName.toString() == DefaultValue::class.java.simpleName }
            ?.let {
                it.findAnnotationValue("value")!!.value.toString() to
                        it.findAnnotationValue("nullable")!!.value!!.toString().toBoolean()
            }


    override fun toTargetTypeStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        commaOrEmpty: String
    ): DtoMembersStrategy.Statement? {
        val propertyName = rootDtoMembersStrategy.toPropertyName(fieldInfo)
        val assignmentContext = assignmentCtxForToTargetType(rootDtoMembersStrategy.toPropertyTypeName(fieldInfo), fieldInfo)
        val maybeNamedParam = if(annotatedElementInfo.primaryTargetTypeElement.isKotlin() || !fieldInfo.isConstructorParam) "$propertyName = " else ""
        return DtoMembersStrategy.Statement("${maybeNamedParam}this.$propertyName${assignmentContext.fallbackValue}$commaOrEmpty", assignmentContext.fallbackArgs)
    }

    override fun maybeCheckForNull(
            fieldInfo: AnnotatedElementFieldInfo,
            assignmentContext: AssignmentContext
    ): AssignmentContext {
        val isSourceNotNull = rootDtoMembersStrategy.isNonNull(fieldInfo, assignmentContext.source)
        val isTargetNullable = rootDtoMembersStrategy.isNullable(fieldInfo, assignmentContext.target)
        return if (isSourceNotNull || isTargetNullable) AssignmentContext.EMPTY
        else assignmentContext
    }

    override fun isNullable(
            fieldInfo: AnnotatedElementFieldInfo, fieldContext: FieldContext
    ): Boolean = when (fieldContext) {
        FieldContext.GENERATED_TYPE -> rootDtoMembersStrategy.toPropertyTypeName(fieldInfo).isNullable
        FieldContext.TARGET_TYPE -> fieldInfo.variableElement.isNullable()
        FieldContext.MIXIN_TYPE -> true
    }


    override fun toPatchStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        commaOrEmpty: String
    ): DtoMembersStrategy.Statement? {
        return when{
            fieldInfo.isConstructorParam
                    && annotatedElementInfo.updateRequiresNewInstance->
                rootDtoMembersStrategy.toConstructorOrCopyPatchStatement(fieldIndex, fieldInfo, annotatedElementInfo, commaOrEmpty)
            else -> rootDtoMembersStrategy.toMutationPatchStatement(fieldIndex, fieldInfo, annotatedElementInfo)
        }
    }


    override fun toConstructorOrCopyPatchStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        commaOrEmpty: String
    ): DtoMembersStrategy.Statement? {
        val propertyName = rootDtoMembersStrategy.toPropertyName(fieldInfo)
        val maybeNamedParam = if(annotatedElementInfo.primaryTargetTypeElement.isKotlin() || !fieldInfo.isConstructorParam) "" +
                "$propertyName = " else ""
        val isUpdatable = annotatedElementInfo.isUpdatable(fieldInfo)
        val maybeAndIfEmpty = if(fieldInfo.variableElement.isIterable()) " && this.${propertyName}!!.isNotEmpty()" else ""
        return if(fieldInfo.isMutableVariable || !annotatedElementInfo.primaryTargetTypeElement.isKotlin())
            if(isUpdatable || annotatedElementInfo.updateRequiresNewInstance)
                DtoMembersStrategy.Statement("${maybeNamedParam}if(this.$propertyName != null$maybeAndIfEmpty) this.$propertyName!! else original.$propertyName$commaOrEmpty")
            else DtoMembersStrategy.Statement("${maybeNamedParam}errNonUpdatableOrOriginalValue(\"$propertyName\", this.$propertyName, original.$propertyName)$commaOrEmpty")
        else DtoMembersStrategy.Statement("// Ignored since immutable and not part of constructor: $propertyName")
    }

    override fun toMutationPatchStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo
    ): DtoMembersStrategy.Statement {
        val propertyName = rootDtoMembersStrategy.toPropertyName(fieldInfo)
        val maybeAndIfEmpty = if(fieldInfo.variableElement.isIterable()) " && this.${propertyName}!!.isNotEmpty()" else ""
        val isUpdatable = annotatedElementInfo.isUpdatable(fieldInfo)
        return if(isUpdatable)
            DtoMembersStrategy.Statement("if(this.$propertyName != null$maybeAndIfEmpty) patched.$propertyName = this.$propertyName!!")
        else DtoMembersStrategy.Statement("errNonUpdatableOrOriginalValue(\"$propertyName\", this.$propertyName, original.$propertyName)")
    }


    override fun toAltConstructorStatement(
            fieldIndex: Int,
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            propertyName: String,
            propertyType: TypeName,
            commaOrEmpty: String
    ): DtoMembersStrategy.Statement? {
        val assignmentContext = assignmentCtxForToAltConstructor(propertyName)
        val maybeNullFallback = maybeCheckForNull(fieldInfo, assignmentContext)
        val maybeNamedParam = if(annotatedElementInfo.primaryTargetTypeElement.isKotlin()) "$propertyName = " else ""
        val toMutableSuffix = maybeToMutableCollectionSuffix(fieldInfo)
        return DtoMembersStrategy.Statement("      ${maybeNamedParam}original.$propertyName$toMutableSuffix${maybeNullFallback.fallbackValue}$commaOrEmpty", maybeNullFallback.fallbackArgs)
    }

    override fun toCreatorStatement(
            fieldIndex: Int,
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            propertyName: String,
            propertyType: TypeName,
            commaOrEmpty: String
    ): DtoMembersStrategy.Statement? {
        val assignmentContext = assignmentCtxForOwnCreator(propertyName)
        val maybeNullFallback = maybeCheckForNull(fieldInfo, assignmentContext)
        val maybeNamedParam = if(annotatedElementInfo.primaryTargetTypeElement.isKotlin()) "$propertyName = " else ""
        val toMutableSuffix = maybeToMutableCollectionSuffix(fieldInfo)
        return DtoMembersStrategy.Statement("      ${maybeNamedParam}original.$propertyName$toMutableSuffix${maybeNullFallback.fallbackValue}$commaOrEmpty", maybeNullFallback.fallbackArgs)
    }

    override fun processDtoOnlyFields(
            typeSpecBuilder: TypeSpec.Builder,
            annotatedElementInfo: AnnotatedElementInfo,
            fields: List<AnnotatedElementFieldInfo>
    ) {
        fields.forEachIndexed { fieldIndex, originalProperty ->
            val (propertyName, propertyType) = rootDtoMembersStrategy.addProperty(
                originalProperty, annotatedElementInfo, fieldIndex, typeSpecBuilder, fields)
            rootDtoMembersStrategy.fieldProcessed(fieldIndex, originalProperty, annotatedElementInfo, propertyName, propertyType)
        }
    }

    override fun processFields(
            typeSpecBuilder: TypeSpec.Builder,
            annotatedElementInfo: AnnotatedElementInfo,
            fields: List<AnnotatedElementFieldInfo>
    ) {
        val fieldsInScope = fields.filter { it.isInAnnotationScope }
        val constructorParamsInScope = fieldsInScope.filter { it.isConstructorParam }
        var constructorParamsAdded = 0
        fieldsInScope.forEachIndexed { fieldIndex, originalProperty ->
            if(originalProperty.isConstructorParam) constructorParamsAdded++
            val commaOrEmpty = if (constructorParamsAdded < constructorParamsInScope.size && originalProperty.isConstructorParam) "," else ""
            // Tell KotlinPoet that the property is initialized via the constructor parameter,
            // by creating both a constructor param and member property
            val (propertyName, propertyType) = rootDtoMembersStrategy.addProperty(
                originalProperty,
                annotatedElementInfo,
                fieldIndex,
                typeSpecBuilder,
                fields
            )
            // TODO: just separate and decouple the darn component builders already
            // Add line to patch function
            val targetBlockForPatch = when{
                originalProperty.isConstructorParam && annotatedElementInfo.updateRequiresNewInstance
                    -> patchFunctionCodeBuilder.instanceInitArgsBlock
                else -> patchFunctionCodeBuilder.instanceMutationsBlock
            }
            targetBlockForPatch.addStatement(rootDtoMembersStrategy
                .toPatchStatement(fieldIndex, originalProperty, annotatedElementInfo, commaOrEmpty))

            // Add line to map function
            val toTargetTypeCodeBuilder = if(originalProperty.isConstructorParam)
                targetTypeFunctionCodeBuilder.instanceInitArgsBlock
            else if(originalProperty.isMutableVariable) targetTypeFunctionCodeBuilder.instanceMutationsBlock
            else null
            toTargetTypeCodeBuilder?.addStatement(
                    rootDtoMembersStrategy.toTargetTypeStatement(
                            fieldIndex,
                            originalProperty,
                            annotatedElementInfo,
                            commaOrEmpty))

            // Add line to alt constructor
            dtoAltConstructorCodeBuilder.addStatement(
                    rootDtoMembersStrategy.toAltConstructorStatement(
                            fieldIndex,
                            originalProperty,
                            annotatedElementInfo,
                            propertyName,
                            propertyType,
                            if(fieldIndex + 1 < fieldsInScope.size) "," else ""
                    )
            )
            // Add line to create
            creatorFunctionCodeBuilder.addStatement(
                    rootDtoMembersStrategy.toCreatorStatement(
                            fieldIndex,
                            originalProperty,
                            annotatedElementInfo,
                            propertyName,
                            propertyType,
                            if(fieldIndex + 1 < fieldsInScope.size) "," else ""
                    )
            )
            //
            rootDtoMembersStrategy.fieldProcessed(fieldIndex, originalProperty, annotatedElementInfo, propertyName, propertyType)
        }
    }

    override fun addProperty(
            originalProperty: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            fieldIndex: Int,
            typeSpecBuilder: TypeSpec.Builder,
            fields: List<AnnotatedElementFieldInfo>
    ): Pair<String, TypeName> {
        val propertyName = rootDtoMembersStrategy.toPropertyName(originalProperty)
        val propertyDefaults = rootDtoMembersStrategy.toDefaultValueExpression(originalProperty)
        val propertyType = rootDtoMembersStrategy
                .toPropertyTypeName(originalProperty)
                .let { propType ->
                    if (propertyDefaults != null) propType.copy(nullable = propertyDefaults.second)
                    else propType
                }
        dtoConstructorBuilder.addParameter(
                ParameterSpec.builder(propertyName, propertyType)
                        .apply { propertyDefaults?.first?.let { defaultValue(it) } }.build()
        )
        val propertySpecBuilder = rootDtoMembersStrategy.toPropertySpecBuilder(
            fieldIndex, originalProperty, annotatedElementInfo, propertyName, propertyType)
        rootDtoMembersStrategy.addPropertyAnnotations(propertySpecBuilder, originalProperty)
        typeSpecBuilder.addProperty(propertySpecBuilder.build())
        return Pair(propertyName, propertyType)
    }

    /** Override to add additional functionality to your [DtoMembersStrategy] implementation */
    override fun fieldProcessed(
            fieldIndex: Int,
            originalProperty: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            propertyName: String,
            propertyType: TypeName
    ) {
        // NO-OP
    }

    override fun toPropertySpecBuilder(
            fieldIndex: Int,
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            propertyName: String,
            propertyType: TypeName
    ): PropertySpec.Builder = PropertySpec.builder(propertyName, propertyType)
            .mutable(defaultMutable())
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
        creatorFunctionCodeBuilder.addStatement(")")

        // Add functions
        typeSpecBuilder
                .primaryConstructor(dtoConstructorBuilder.build())
                .addType(companionObject.addFunction(
                        creatorFunctionBuilder
                                .addStatement(creatorFunctionCodeBuilder.build().toString())
                                .build()
                ).build())
        typeSpecBuilder.addFunction(patchFunctionBuilder.addCode(patchFunctionCodeBuilder.toCodeBlock()).build())
        typeSpecBuilder.addFunction(targetTypeFunctionBuilder.addCode(targetTypeFunctionCodeBuilder.toCodeBlock()).build())
    }

    override fun addAltConstructor(typeSpecBuilder: TypeSpec.Builder, dtoAltConstructorBuilder: FunSpec.Builder) {
        typeSpecBuilder.addFunction(dtoAltConstructorBuilder.build())
    }

    override val processingEnvironment: ProcessingEnvironment
        get() = annotatedElementInfo.processingEnvironment
}