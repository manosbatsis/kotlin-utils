package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.DtoStrategy
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import javax.lang.model.element.VariableElement

/** Defines a composite DTO source creation strategy for annotation processing */
interface DtoStrategyComposition : DtoStrategy {
    val annotatedElementInfo: AnnotatedElementInfo
    val dtoNameStrategy: DtoNameStrategy
    val dtoTypeStrategy: DtoTypeStrategy
    val dtoMembersStrategy: DtoMembersStrategy

    override fun dtoTypeSpec(): TypeSpec = dtoTypeSpecBuilder().build()

    override fun dtoTypeSpecBuilder(): TypeSpec.Builder {
        val dtoTypeSpecBuilder = TypeSpec.classBuilder(dtoNameStrategy.getClassName())
        dtoTypeStrategy.addSuperTypes(dtoTypeSpecBuilder)
        dtoTypeStrategy.addModifiers(dtoTypeSpecBuilder)
        dtoTypeStrategy.addKdoc(dtoTypeSpecBuilder)
        dtoTypeStrategy.addAnnotations(dtoTypeSpecBuilder)
        addMembers(dtoTypeSpecBuilder)
        annotatedElementInfo.primaryTargetTypeElement.typeParameters.forEach {
            dtoTypeSpecBuilder.addTypeVariable(
                    TypeVariableName.invoke(it.simpleName.toString(), *it.bounds.map { it.asTypeName() }.toTypedArray()))
        }

        return dtoTypeSpecBuilder
    }

    /** Process original type fields and add DTO members */
    override fun addMembers(typeSpecBuilder: TypeSpec.Builder) {
        dtoMembersStrategy.processFields(typeSpecBuilder, getFieldsToProcess())
        dtoMembersStrategy.processDtoOnlyFields(typeSpecBuilder, getFieldsFromMixin())
        dtoMembersStrategy.finalize(typeSpecBuilder)
    }

    override fun getIgnoredFieldNames(): List<String> =
            annotatedElementInfo.ignoreProperties

    override fun getFieldsToProcess(): List<VariableElement> =
            annotatedElementInfo.primaryTargetTypeElementFields
                    .excludeNames(getIgnoredFieldNames())

    override fun getFieldsFromMixin(): List<VariableElement> =
            annotatedElementInfo.primaryTargetTypeElementFields.toSimpleNames()
                    .plus(getIgnoredFieldNames())
                    .let { annotatedElementInfo.mixinTypeElementFields.excludeNames(it) }
}

