package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import javax.lang.model.element.VariableElement


/** Delegate-based implementation of [DtoStrategy] */
open class CompositeDtoStrategy(
        val annotatedElementInfo: AnnotatedElementInfo,
        val dtoNameStrategy: DtoNameStrategy = SimpleDtoNameStrategy(annotatedElementInfo),
        val dtoTypeStrategy: DtoTypeStrategy = SimpleDtoTypeStrategy(annotatedElementInfo),
        val dtoMembersStrategy: DtoMembersStrategy = SimpleDtoMembersStrategy(
                annotatedElementInfo, dtoNameStrategy, dtoTypeStrategy)
) : DtoStrategy {
    constructor(
            annotatedElementInfo: AnnotatedElementInfo,
            composition: DtoStrategyComposition
    ) : this(
            annotatedElementInfo,
            composition.dtoNameStrategy,
            composition.dtoTypeStrategy,
            composition.dtoMembersStrategy
    )

    override fun dtoTypeSpec(): TypeSpec = dtoTypeSpecBuilder().build()

    override fun dtoTypeSpecBuilder(): Builder {
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
    override fun addMembers(typeSpecBuilder: Builder) {
        dtoMembersStrategy.processFields(typeSpecBuilder, getFieldsToProcess())
    }

    override fun getFieldsToProcess(): List<VariableElement> =
            annotatedElementInfo.primaryTargetTypeElementFields
                    .filterNot { annotatedElementInfo.ignoreProperties.contains(it.simpleName.toString()) }


}
