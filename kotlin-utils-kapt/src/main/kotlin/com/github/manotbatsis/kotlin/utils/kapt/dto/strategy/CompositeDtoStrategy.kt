package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement


/** Delegate-based implementation of [DtoStrategy] */
open class CompositeDtoStrategy(
        override val annotatedElementInfo: AnnotatedElementInfo,
        override val processingEnvironment: ProcessingEnvironment = annotatedElementInfo.processingEnvironment,
        val dtoNameStrategy: DtoNameStrategy = SimpleDtoNameStrategy(annotatedElementInfo),
        val dtoMembersStrategy: DtoMembersStrategy = SimpleDtoMembersStrategy(annotatedElementInfo),
        val dtoTypeStrategy: DtoTypeStrategy = SimpleDtoTypeStrategy(annotatedElementInfo)
) : DtoStrategy,
        AnnotatedElementInfo by annotatedElementInfo,
        DtoTypeStrategy by dtoTypeStrategy,
        DtoNameStrategy by dtoNameStrategy,
        DtoMembersStrategy by dtoMembersStrategy {



    constructor(
            annotatedElementInfo: AnnotatedElementInfo,
            composition: DtoStrategyComposition
    ) : this(
            annotatedElementInfo,
            annotatedElementInfo.processingEnvironment,
            composition.dtoNameStrategy(annotatedElementInfo),
            composition.dtoMembersStrategy( annotatedElementInfo),
            composition.dtoTypeStrategy(annotatedElementInfo)
    )

    override fun dtoTypeSpec(): TypeSpec = dtoTypeSpecBuilder().build()

    override fun dtoTypeSpecBuilder(): Builder {
        val dtoTypeSpecBuilder = TypeSpec.classBuilder(getClassName())
        addSuperTypes(dtoTypeSpecBuilder)
        addModifiers(dtoTypeSpecBuilder)
        addKdoc(dtoTypeSpecBuilder)
        addAnnotations(dtoTypeSpecBuilder)
        addMembers(dtoTypeSpecBuilder)
        primaryTargetTypeElement.typeParameters.forEach {
            dtoTypeSpecBuilder.addTypeVariable(
                    TypeVariableName.invoke(it.simpleName.toString(), *it.bounds.map { it.asTypeName() }.toTypedArray()))
        }

        return dtoTypeSpecBuilder
    }


    /** Process original type fields and add DTO members */
    override fun addMembers(typeSpecBuilder: Builder) {
        this.processFields(typeSpecBuilder, getFieldsToProcess())
    }

    override fun getFieldsToProcess(): List<VariableElement> =
            primaryTargetTypeElementFields.filterNot { ignoreProperties.contains(it.simpleName.toString()) }


}
