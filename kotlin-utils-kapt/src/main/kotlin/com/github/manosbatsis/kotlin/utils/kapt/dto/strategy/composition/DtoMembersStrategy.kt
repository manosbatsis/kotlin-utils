package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.AssignmentContext
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.FieldContext
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
    fun toDefaultValueExpression(variableElement: VariableElement): Pair<String, Boolean>?
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
    fun addProperty(originalProperty: VariableElement, fieldIndex: Int, typeSpecBuilder: Builder): Pair<String, TypeName>
    fun findDefaultValueAnnotationValue(variableElement: VariableElement): Pair<String, Boolean>?
    fun isNullable(variableElement: VariableElement, fieldContext: FieldContext): Boolean
    fun isNonNull(variableElement: VariableElement, fieldContext: FieldContext): Boolean = !isNullable(variableElement, fieldContext)
    fun maybeCheckForNull(variableElement: VariableElement, assignmentContext: AssignmentContext): AssignmentContext
}


