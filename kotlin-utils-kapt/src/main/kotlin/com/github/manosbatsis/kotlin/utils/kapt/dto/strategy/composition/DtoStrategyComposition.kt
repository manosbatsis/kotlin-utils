package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.DtoStrategy
import com.squareup.kotlinpoet.*
import javax.lang.model.element.VariableElement

/**
 * Extends [DtoStrategyLesserComposition] with a [DtoMembersStrategy] member and
 * misc default method implementations. Used as a composite DTO source creation strategy
 * for annotation processing
 */
interface DtoStrategyComposition : DtoStrategyLesserComposition, DtoMembersStrategy, DtoStrategy {
    val dtoMembersStrategy: DtoMembersStrategy

    override fun dtoTypeSpec(): TypeSpec = dtoTypeSpecBuilder().build()

    override fun dtoTypeSpecBuilder(): TypeSpec.Builder {
        val dtoTypeSpecBuilder = TypeSpec.classBuilder(getClassName())
        addSuperTypes(dtoTypeSpecBuilder)
        addModifiers(dtoTypeSpecBuilder)
        addKdoc(dtoTypeSpecBuilder)
        addAnnotations(dtoTypeSpecBuilder)
        addMembers(dtoTypeSpecBuilder)
        annotatedElementInfo.primaryTargetTypeElement.typeParameters.forEach {
            dtoTypeSpecBuilder.addTypeVariable(
                    TypeVariableName.invoke(it.simpleName.toString(), *it.bounds.map { it.asTypeName() }.toTypedArray()))
        }

        return dtoTypeSpecBuilder
    }

    /** Process original type fields and add DTO members */
    override fun addMembers(typeSpecBuilder: TypeSpec.Builder) {
        processFields(typeSpecBuilder, getFieldsToProcess())
        processDtoOnlyFields(typeSpecBuilder, getExtraFieldsFromMixin())
        finalize(typeSpecBuilder)
    }

    override fun getFieldExcludes(): List<String> =
            annotatedElementInfo.ignoreProperties

    override fun getFieldsToProcess(): List<VariableElement> =
            annotatedElementInfo.primaryTargetTypeElementFields.filtered()

    override fun getExtraFieldsFromMixin(): List<VariableElement> =
            annotatedElementInfo.primaryTargetTypeElementFields.toSimpleNames()
                    .plus(getFieldExcludes())
                    .let { annotatedElementInfo.mixinTypeElementFields.excludeNames(it) }

    // DtoMembersStrategy
    override fun maybeCheckForNull(
            variableElement: VariableElement,
            assignmentContext: AssignmentContext
    ): String = dtoMembersStrategy.maybeCheckForNull(variableElement, assignmentContext)

    override fun isNullable(variableElement: VariableElement, fieldContext: FieldContext): Boolean =
            dtoMembersStrategy.isNullable(variableElement, fieldContext)

    /** Override to modify processing of individual fields */
    override fun processFields(typeSpecBuilder: TypeSpec.Builder, fields: List<VariableElement>) =
            dtoMembersStrategy.processFields(typeSpecBuilder, fields)

    /**
     * Override to modify processing of DTO-specific fields,
     * e.g. from mixins
     */
    override fun processDtoOnlyFields(
            typeSpecBuilder: TypeSpec.Builder,
            fields: List<VariableElement>
    ) = dtoMembersStrategy.processDtoOnlyFields(typeSpecBuilder, fields)

    /** Override to change the property-level annotations applied   */
    override fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, variableElement: VariableElement) =
            dtoMembersStrategy.addPropertyAnnotations(propertySpecBuilder, variableElement)

    override fun getToPatchedFunctionBuilder(originalTypeParameter: ParameterSpec): FunSpec.Builder =
            dtoMembersStrategy.getToPatchedFunctionBuilder(originalTypeParameter)

    override fun getToTargetTypeFunctionBuilder(): FunSpec.Builder =
            dtoMembersStrategy.getToTargetTypeFunctionBuilder()

    override fun toPropertyName(variableElement: VariableElement): String =
            dtoMembersStrategy.toPropertyName(variableElement)

    override fun toPropertyTypeName(variableElement: VariableElement): TypeName =
            dtoMembersStrategy.toPropertyTypeName(variableElement)

    override fun toDefaultValueExpression(variableElement: VariableElement): String? =
            dtoMembersStrategy.toDefaultValueExpression(variableElement)

    override fun toTargetTypeStatement(
            fieldIndex: Int, variableElement: VariableElement, commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toTargetTypeStatement(fieldIndex, variableElement, commaOrEmpty)

    override fun toPatchStatement(
            fieldIndex: Int, variableElement: VariableElement, commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toPatchStatement(fieldIndex, variableElement, commaOrEmpty)

    override fun toAltConstructorStatement(
            fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName, commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toAltConstructorStatement(
            fieldIndex, variableElement, propertyName, propertyType, commaOrEmpty)

    override fun toPropertySpecBuilder(
            fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName
    ): PropertySpec.Builder = dtoMembersStrategy.toPropertySpecBuilder(
            fieldIndex, variableElement, propertyName, propertyType)

    override fun fieldProcessed(
            fieldIndex: Int, originalProperty: VariableElement, propertyName: String, propertyType: TypeName
    ) = dtoMembersStrategy.fieldProcessed(
            fieldIndex, originalProperty, propertyName, propertyType)

    override fun getAltConstructorBuilder(): FunSpec.Builder = dtoMembersStrategy.getAltConstructorBuilder()

    override fun getCompanionBuilder(): TypeSpec.Builder = dtoMembersStrategy.getCompanionBuilder()

    override fun getCreatorFunctionBuilder(originalTypeParameter: ParameterSpec): FunSpec.Builder =
            dtoMembersStrategy.getCreatorFunctionBuilder(originalTypeParameter)

    override fun toCreatorStatement(
            fieldIndex: Int, variableElement: VariableElement, propertyName: String, propertyType: TypeName, commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toCreatorStatement(
            fieldIndex, variableElement, propertyName, propertyType, commaOrEmpty)

    override fun addAltConstructor(typeSpecBuilder: TypeSpec.Builder, dtoAltConstructorBuilder: FunSpec.Builder) =
            dtoMembersStrategy.addAltConstructor(typeSpecBuilder, dtoAltConstructorBuilder)

    override fun finalize(typeSpecBuilder: TypeSpec.Builder) = dtoMembersStrategy.finalize(typeSpecBuilder)


    override fun addProperty(
            originalProperty: VariableElement, fieldIndex: Int, typeSpecBuilder: TypeSpec.Builder
    ): Pair<String, TypeName> = dtoMembersStrategy.addProperty(originalProperty, fieldIndex, typeSpecBuilder)

    override fun findDefaultValueAnnotationValue(variableElement: VariableElement): String? =
            dtoMembersStrategy.findDefaultValueAnnotationValue(variableElement)
}

