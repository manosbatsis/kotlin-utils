package com.github.manotbatsis.kotlin.utils.dto

import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

data class DtoTypeSpecBuilder(
        val processingEnvironment: ProcessingEnvironment,
        val originalTypeElement: TypeElement,
        val fields: List<VariableElement> = emptyList(),
        val targetPackage: String = originalTypeElement.asClassName().packageName,
        val copyAnnotationPackages: Iterable<String> = emptyList(),
        private val dtoStrategyClass: Class<DefaultDtoTypeSpecBuilderStrategy> = DefaultDtoTypeSpecBuilderStrategy::class.java
) {
    val originalTypeName by lazy { originalTypeElement.asType().asTypeName() }
    val dtoStrategy by lazy {
        dtoStrategyClass
                .getConstructor(ProcessingEnvironment::class.java, DtoTypeSpecBuilder::class.java)
                .newInstance(processingEnvironment, this)
    }

    fun build() = dtoStrategy.dtoTypeSpec()
    fun builder() = dtoStrategy.dtoTypeSpecBuilder()
}