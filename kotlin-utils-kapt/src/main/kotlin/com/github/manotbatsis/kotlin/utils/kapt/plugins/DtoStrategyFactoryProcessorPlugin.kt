package com.github.manotbatsis.kotlin.utils.kapt.plugins

import com.github.manotbatsis.kotlin.utils.kapt.dto.strategy.DtoStrategy
import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo


interface DtoStrategyFactoryProcessorPlugin : AnnotationProcessorPlugin {

    fun getStrategyClass(strategy: String): Class<out DtoStrategy>

    fun createStrategy(
            annotatedElementInfo: AnnotatedElementInfo, strategy: String
    ): DtoStrategy
}


abstract class AbstractDtoStrategyFactoryProcessorPlugin: DtoStrategyFactoryProcessorPlugin {

    override fun createStrategy(
            annotatedElementInfo: AnnotatedElementInfo, strategy: String
    ): DtoStrategy {

        val dtoStrategy = getStrategyClass(strategy)
                .getConstructor( AnnotatedElementInfo::class.java )
                .newInstance(annotatedElementInfo)

        return dtoStrategy
    }

}
