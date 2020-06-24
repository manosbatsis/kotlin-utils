package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manotbatsis.kotlin.utils.api.Dto
import com.github.manotbatsis.kotlin.utils.kapt.dto.DtoInputContext
import com.github.manotbatsis.kotlin.utils.kapt.dto.DtoInputContextAware
import com.squareup.kotlinpoet.KModifier.DATA
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.asClassName
import javax.annotation.processing.ProcessingEnvironment


interface DtoTypeStrategy : DtoInputContextAware {
    /** Override to change the type-level annotations applied to the DTO  */
    fun addAnnotations(typeSpecBuilder: Builder)

    /** Override to change the type-level KDoc applied to the DTO  */
    fun addKdoc(typeSpecBuilder: Builder)

    /** Override to change the type-level [KModifier]s applied to the DTO  */
    fun addModifiers(typeSpecBuilder: Builder)

    /** Override to change the super types the DTO extends or implements  */
    fun addSuperTypes(typeSpecBuilder: Builder)

}

open class SimpleDtoTypeStrategy(
        override val processingEnvironment: ProcessingEnvironment,
        override val dtoInputContext: DtoInputContext
) : DtoTypeStrategy, ProcessingEnvironmentAware {

    override fun addAnnotations(typeSpecBuilder: Builder) {
        typeSpecBuilder.copyAnnotationsByBasePackage(dtoInputContext.originalTypeElement, dtoInputContext.copyAnnotationPackages)
    }

    override fun addKdoc(typeSpecBuilder: Builder) {
        typeSpecBuilder.addKdoc("A [%T]-specific [%T] implementation", dtoInputContext.originalTypeName, Dto::class)
    }

    override fun addModifiers(typeSpecBuilder: Builder) {
        typeSpecBuilder.addModifiers(DATA)
    }

    override fun addSuperTypes(typeSpecBuilder: Builder) {
        typeSpecBuilder.addSuperinterface(Dto::class.asClassName().parameterizedBy(dtoInputContext.originalTypeName))
    }

}



