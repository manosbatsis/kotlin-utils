package com.github.manotbatsis.kotlin.utils.kapt.dto

import com.github.manotbatsis.kotlin.utils.kapt.dto.strategy.CompositeDtoStrategy
import com.github.manotbatsis.kotlin.utils.kapt.dto.strategy.DtoStrategy
import com.github.manotbatsis.kotlin.utils.kapt.dto.strategy.DtoStrategyComposition
import com.github.manotbatsis.kotlin.utils.kapt.dto.strategy.SimpleDtoStrategyComposition
import com.squareup.kotlinpoet.asTypeName
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

data class DtoInputContext(
        val processingEnvironment: ProcessingEnvironment,
        val originalTypeElement: TypeElement,
        val fields: List<VariableElement> = emptyList(),
        val copyAnnotationPackages: Iterable<String> = emptyList(),
        val dtoStrategyClass: Class<out DtoStrategy> = CompositeDtoStrategy::class.java,
        val dtoStrategyDtoStrategyComposition: DtoStrategyComposition = SimpleDtoStrategyComposition
) {


    val originalTypeName by lazy { originalTypeElement.asType().asTypeName() }

    // TODO: extract to factory
    val dtoStrategy by lazy {
        if (CompositeDtoStrategy::class.java.isAssignableFrom(dtoStrategyClass))
            dtoStrategyClass.getConstructor(
                    ProcessingEnvironment::class.java,
                    DtoInputContext::class.java,
                    DtoStrategyComposition::class.java)
                    .newInstance(processingEnvironment, this, dtoStrategyDtoStrategyComposition)
        else dtoStrategyClass.getConstructor(
                ProcessingEnvironment::class.java,
                DtoInputContext::class.java)
                .newInstance(processingEnvironment, this)
    }

    fun builder() = dtoStrategy.dtoTypeSpecBuilder()

}