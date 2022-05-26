package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.AssignmentContext
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.FieldContext
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementFieldInfo
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.TypeSpec.Builder
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement

/**
 * Used by [DtoStrategyComposition] implementations to delegate
 * processing related to the generated DTO members.
 */
interface DtoMembersStrategy : ProcessingEnvironmentAware {

    data class Statement(val format: String, val args: List<Any> = emptyList())

    val annotatedElementInfo: AnnotatedElementInfo
    val dtoNameStrategy: DtoNameStrategy
    val dtoTypeStrategy: DtoTypeStrategy

    override val processingEnvironment: ProcessingEnvironment
        get() = annotatedElementInfo.processingEnvironment


    fun useMutableIterables(): Boolean = false
    fun defaultNullable(): Boolean = true
    fun defaultMutable(): Boolean = true

    /** Override to modify processing of individual fields */
    fun processFields(
        typeSpecBuilder: Builder,
        annotatedElementInfo: AnnotatedElementInfo,
        fields: List<AnnotatedElementFieldInfo>
    )

    /**
     * Override to modify processing of DTO-specific fields,
     * e.g. from mixins
     */
    fun processDtoOnlyFields(
            typeSpecBuilder: TypeSpec.Builder,
            annotatedElementInfo: AnnotatedElementInfo,
            fields: List<AnnotatedElementFieldInfo>
    )

    /** Override to change the property-level annotations applied   */
    fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, fieldInfo: AnnotatedElementFieldInfo)
    fun getToPatchedFunctionBuilder(
            originalTypeParameter: ParameterSpec
    ): FunSpec.Builder

    fun getToTargetTypeFunctionBuilder(): FunSpec.Builder
    fun toPropertyName(fieldInfo: AnnotatedElementFieldInfo): String
    fun toPropertyTypeName(fieldInfo: AnnotatedElementFieldInfo): TypeName
    fun toDefaultValueExpression(fieldInfo: AnnotatedElementFieldInfo): Pair<String, Boolean>?
    fun toTargetTypeStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        commaOrEmpty: String
    ): Statement?
    fun toPatchStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        commaOrEmpty: String
    ): Statement?

    fun toMutationPatchStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo
    ): Statement

    fun toAltConstructorStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        propertyName: String,
        propertyType: TypeName,
        commaOrEmpty: String
    ): Statement?
    fun toPropertySpecBuilder(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        propertyName: String,
        propertyType: TypeName
    ): PropertySpec.Builder
    fun fieldProcessed(
        fieldIndex: Int,
        originalProperty: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        propertyName: String,
        propertyType: TypeName
    )
    fun getAltConstructorBuilder(): FunSpec.Builder
    fun getCompanionBuilder(): Builder
    fun getCreatorFunctionBuilder(originalTypeParameter: ParameterSpec): FunSpec.Builder
    fun toCreatorStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        propertyName: String,
        propertyType: TypeName,
        commaOrEmpty: String
    ): Statement?
    fun addAltConstructor(typeSpecBuilder: Builder, dtoAltConstructorBuilder: FunSpec.Builder)
    fun finalize(typeSpecBuilder: Builder)
    fun addProperty(
        originalProperty: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        fieldIndex: Int,
        typeSpecBuilder: Builder,
        fields: List<AnnotatedElementFieldInfo>
    ): Pair<String, TypeName>
    fun findDefaultValueAnnotationValue(
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo
    ): Pair<String, Boolean>?
    fun isNullable(fieldInfo: AnnotatedElementFieldInfo, fieldContext: FieldContext): Boolean
    fun isNonNull(fieldInfo: AnnotatedElementFieldInfo, fieldContext: FieldContext): Boolean = !isNullable(fieldInfo, fieldContext)
    fun maybeCheckForNull(fieldInfo: AnnotatedElementFieldInfo, assignmentContext: AssignmentContext): AssignmentContext
    fun toConstructorOrCopyPatchStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        commaOrEmpty: String
    ): Statement?
}


