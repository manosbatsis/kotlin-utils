package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manotbatsis.kotlin.utils.api.Dto
import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.KModifier.DATA
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.asClassName


interface DtoTypeStrategy {
    val annotatedElementInfo: AnnotatedElementInfo
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
        override val annotatedElementInfo: AnnotatedElementInfo
) : DtoTypeStrategy, ProcessingEnvironmentAware {

    override val processingEnvironment = annotatedElementInfo.processingEnvironment
    override fun addAnnotations(typeSpecBuilder: Builder) {
        typeSpecBuilder.copyAnnotationsByBasePackage(
                annotatedElementInfo.primaryTargetTypeElement,
                annotatedElementInfo.copyAnnotationPackages)
    }

    override fun addKdoc(typeSpecBuilder: Builder) {
        typeSpecBuilder.addKdoc("A [%T]-specific [%T] implementation",
                annotatedElementInfo.primaryTargetTypeElement, Dto::class)
    }

    override fun addModifiers(typeSpecBuilder: Builder) {
        typeSpecBuilder.addModifiers(DATA)
    }

    override fun addSuperTypes(typeSpecBuilder: Builder) {
        typeSpecBuilder.addSuperinterface(Dto::class.asClassName().parameterizedBy(
                annotatedElementInfo.primaryTargetTypeElement.asKotlinTypeName()))
    }

}



