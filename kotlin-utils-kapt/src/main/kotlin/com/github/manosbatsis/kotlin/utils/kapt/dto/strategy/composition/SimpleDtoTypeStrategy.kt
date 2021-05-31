package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manosbatsis.kotlin.utils.api.Dto
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import javax.lang.model.element.TypeElement

/** Simple implementation of [DtoTypeStrategy] */
open class SimpleDtoTypeStrategy(
        override val annotatedElementInfo: AnnotatedElementInfo
) : DtoTypeStrategy, ProcessingEnvironmentAware {

    override val processingEnvironment by lazy { annotatedElementInfo.processingEnvironment }

    override fun addAnnotations(typeSpecBuilder: TypeSpec.Builder) {
        with(annotatedElementInfo) {
            listOfNotNull(primaryTargetTypeElement, mixinTypeElement)
                    .forEach { typeSpecBuilder.copyAnnotationsByBasePackage(it, copyAnnotationPackages) }
        }
    }

    override fun addKdoc(typeSpecBuilder: TypeSpec.Builder) {
        typeSpecBuilder.addKdoc("A [%T]-specific [%T] implementation",
                annotatedElementInfo.primaryTargetTypeElement, getDtoInterface())
    }

    override fun addModifiers(typeSpecBuilder: TypeSpec.Builder) {
        typeSpecBuilder.addModifiers(KModifier.DATA)
    }

    override fun addSuperTypes(typeSpecBuilder: TypeSpec.Builder) {
        typeSpecBuilder.addSuperinterface(getParameterizedDtoInterfaceTypeName())
    }

    private fun getParameterizedDtoInterfaceTypeName() =
            getDtoInterface().asClassName().parameterizedBy(
                    annotatedElementInfo.primaryTargetTypeElement.asKotlinTypeName())

    override fun getDtoInterface(): Class<*> = Dto::class.java

    override fun getDtoTarget(): TypeElement = annotatedElementInfo.primaryTargetTypeElement


}