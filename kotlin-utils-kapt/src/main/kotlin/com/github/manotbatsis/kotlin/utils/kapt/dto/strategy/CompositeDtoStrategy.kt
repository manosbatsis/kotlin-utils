package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manotbatsis.kotlin.utils.kapt.dto.DtoInputContext
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.TypeVariableName
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement


/** Delegate-based implementation of [DtoStrategy] */
open class CompositeDtoStrategy(
        override val processingEnvironment: ProcessingEnvironment,
        override val dtoInputContext: DtoInputContext,
        val dtoNameStrategy: DtoNameStrategy = SimpleDtoNameStrategy(
                processingEnvironment, dtoInputContext
        ),
        val dtoMembersStrategy: DtoMembersStrategy = SimpleDtoMembersStrategy(
                processingEnvironment, dtoInputContext
        ),
        val dtoTypeStrategy: DtoTypeStrategy = SimpleDtoTypeStrategy(
                processingEnvironment, dtoInputContext
        )
) : DtoStrategy, ProcessingEnvironmentAware,
        DtoTypeStrategy by dtoTypeStrategy,
        DtoNameStrategy by dtoNameStrategy,
        DtoMembersStrategy by dtoMembersStrategy {


    constructor(
            processingEnvironment: ProcessingEnvironment,
            dtoInputContext: DtoInputContext,
            composition: DtoStrategyComposition
    ) : this(
            processingEnvironment,
            dtoInputContext,
            composition.dtoNameStrategy(processingEnvironment, dtoInputContext),
            composition.dtoMembersStrategy(processingEnvironment, dtoInputContext),
            composition.dtoTypeStrategy(processingEnvironment, dtoInputContext)
    )

    override fun dtoTypeSpec(): TypeSpec = dtoTypeSpecBuilder().build()

    override fun dtoTypeSpecBuilder(): Builder {
        val dtoTypeSpecBuilder = TypeSpec.classBuilder(getClassName())
        addSuperTypes(dtoTypeSpecBuilder)
        addModifiers(dtoTypeSpecBuilder)
        addKdoc(dtoTypeSpecBuilder)
        addAnnotations(dtoTypeSpecBuilder)
        addMembers(dtoTypeSpecBuilder)
        this.dtoInputContext.originalTypeElement.typeParameters.forEach {
            dtoTypeSpecBuilder.addTypeVariable(TypeVariableName.invoke(it.simpleName.toString(), *it.bounds.map { it.asKotlinTypeName() }.toTypedArray()))
        }

        return dtoTypeSpecBuilder
    }


    /** Process original type fields and add DTO members */
    override fun addMembers(typeSpecBuilder: Builder) {
        this.processFields(typeSpecBuilder, getFieldsToProcess())
    }

    override fun getFieldsToProcess(): List<VariableElement> =
            if (dtoInputContext.fields.isNotEmpty()) dtoInputContext.fields
            else dtoInputContext.originalTypeElement.accessibleConstructorParameterFields()


}
