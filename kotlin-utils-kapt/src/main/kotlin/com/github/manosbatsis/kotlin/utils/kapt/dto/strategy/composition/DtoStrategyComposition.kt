package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.DtoStrategy
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.AssignmentContext
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.FieldContext
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementFieldInfo
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.*

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
        processFields(typeSpecBuilder, annotatedElementInfo, getFieldsToProcess())
        processDtoOnlyFields(typeSpecBuilder, annotatedElementInfo, getExtraFieldsFromMixin())
        finalize(typeSpecBuilder)
    }

    override fun getFieldExcludes(): List<String> =
            annotatedElementInfo.ignoreProperties

    override fun getFieldsToProcess(): List<AnnotatedElementFieldInfo> =
            annotatedElementInfo.primaryTargetTypeElementFields.filtered()

    override fun getExtraFieldsFromMixin(): List<AnnotatedElementFieldInfo> =
            annotatedElementInfo.primaryTargetTypeElementFields.toSimpleNames()
                    .plus(getFieldExcludes())
                    .let { annotatedElementInfo.mixinTypeElementFields.excludeNames(it) }

    // DtoMembersStrategy
    override fun maybeCheckForNull(
            fieldInfo: AnnotatedElementFieldInfo,
            assignmentContext: AssignmentContext
    ): AssignmentContext = dtoMembersStrategy.maybeCheckForNull(fieldInfo, assignmentContext)

    override fun isNullable(fieldInfo: AnnotatedElementFieldInfo, fieldContext: FieldContext): Boolean =
            dtoMembersStrategy.isNullable(fieldInfo, fieldContext)

    /** Override to modify processing of individual fields */
    override fun processFields(
        typeSpecBuilder: TypeSpec.Builder,
        annotatedElementInfo: AnnotatedElementInfo,
        fields: List<AnnotatedElementFieldInfo>
    ) = dtoMembersStrategy.processFields(typeSpecBuilder, annotatedElementInfo, fields)

    /**
     * Override to modify processing of DTO-specific fields,
     * e.g. from mixins
     */
    override fun processDtoOnlyFields(
            typeSpecBuilder: TypeSpec.Builder,
            annotatedElementInfo: AnnotatedElementInfo,
            fields: List<AnnotatedElementFieldInfo>
    ) = dtoMembersStrategy.processDtoOnlyFields(typeSpecBuilder, annotatedElementInfo, fields)

    /** Override to change the property-level annotations applied   */
    override fun addPropertyAnnotations(propertySpecBuilder: PropertySpec.Builder, fieldInfo: AnnotatedElementFieldInfo) =
            dtoMembersStrategy.addPropertyAnnotations(propertySpecBuilder, fieldInfo)

    override fun getToPatchedFunctionBuilder(originalTypeParameter: ParameterSpec): FunSpec.Builder =
            dtoMembersStrategy.getToPatchedFunctionBuilder(originalTypeParameter)

    override fun getToTargetTypeFunctionBuilder(): FunSpec.Builder =
            dtoMembersStrategy.getToTargetTypeFunctionBuilder()

    override fun toPropertyName(fieldInfo: AnnotatedElementFieldInfo): String =
            dtoMembersStrategy.toPropertyName(fieldInfo)

    override fun toPropertyTypeName(fieldInfo: AnnotatedElementFieldInfo): TypeName =
            dtoMembersStrategy.toPropertyTypeName(fieldInfo)

    override fun toDefaultValueExpression(fieldInfo: AnnotatedElementFieldInfo): Pair<String, Boolean>? =
            dtoMembersStrategy.toDefaultValueExpression(fieldInfo)

    override fun toTargetTypeStatement(
            fieldIndex: Int,
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toTargetTypeStatement(
        fieldIndex, fieldInfo, annotatedElementInfo, commaOrEmpty
    )

    override fun toPatchStatement(
            fieldIndex: Int,
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toPatchStatement(
        fieldIndex, fieldInfo, annotatedElementInfo, commaOrEmpty
    )


    override fun toConstructorOrCopyPatchStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo,
        commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toConstructorOrCopyPatchStatement(
        fieldIndex, fieldInfo, annotatedElementInfo, commaOrEmpty
    )

    override fun toMutationPatchStatement(
        fieldIndex: Int,
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo
    ): DtoMembersStrategy.Statement = dtoMembersStrategy.toMutationPatchStatement(
        fieldIndex, fieldInfo, annotatedElementInfo
    )


    override fun toAltConstructorStatement(
            fieldIndex: Int,
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            propertyName: String,
            propertyType: TypeName,
            commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toAltConstructorStatement(
            fieldIndex, fieldInfo, annotatedElementInfo, propertyName, propertyType, commaOrEmpty
    )

    override fun toPropertySpecBuilder(
            fieldIndex: Int,
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            propertyName: String,
            propertyType: TypeName
    ): PropertySpec.Builder = dtoMembersStrategy.toPropertySpecBuilder(
            fieldIndex, fieldInfo, annotatedElementInfo, propertyName, propertyType
    )

    override fun fieldProcessed(
            fieldIndex: Int,
            originalProperty: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            propertyName: String,
            propertyType: TypeName
    ) = dtoMembersStrategy.fieldProcessed(
            fieldIndex, originalProperty, annotatedElementInfo, propertyName, propertyType
    )

    override fun getAltConstructorBuilder(): FunSpec.Builder = dtoMembersStrategy.getAltConstructorBuilder()

    override fun getCompanionBuilder(): TypeSpec.Builder = dtoMembersStrategy.getCompanionBuilder()

    override fun getCreatorFunctionBuilder(originalTypeParameter: ParameterSpec): FunSpec.Builder =
            dtoMembersStrategy.getCreatorFunctionBuilder(originalTypeParameter)

    override fun toCreatorStatement(
            fieldIndex: Int,
            fieldInfo: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            propertyName: String,
            propertyType: TypeName,
            commaOrEmpty: String
    ): DtoMembersStrategy.Statement? = dtoMembersStrategy.toCreatorStatement(
            fieldIndex, fieldInfo, annotatedElementInfo, propertyName, propertyType, commaOrEmpty
    )

    override fun addAltConstructor(typeSpecBuilder: TypeSpec.Builder, dtoAltConstructorBuilder: FunSpec.Builder) =
            dtoMembersStrategy.addAltConstructor(typeSpecBuilder, dtoAltConstructorBuilder
            )

    override fun finalize(typeSpecBuilder: TypeSpec.Builder) = dtoMembersStrategy.finalize(typeSpecBuilder)


    override fun addProperty(
            originalProperty: AnnotatedElementFieldInfo,
            annotatedElementInfo: AnnotatedElementInfo,
            fieldIndex: Int,
            typeSpecBuilder: TypeSpec.Builder,
            fields: List<AnnotatedElementFieldInfo>
    ): Pair<String, TypeName> = dtoMembersStrategy.addProperty(
        originalProperty, annotatedElementInfo, fieldIndex, typeSpecBuilder, fields
    )

    override fun findDefaultValueAnnotationValue(
        fieldInfo: AnnotatedElementFieldInfo,
        annotatedElementInfo: AnnotatedElementInfo
    ): Pair<String, Boolean>? =
            dtoMembersStrategy.findDefaultValueAnnotationValue(fieldInfo, annotatedElementInfo)
}

