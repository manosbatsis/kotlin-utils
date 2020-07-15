package com.github.manotbatsis.kotlin.utils.kapt.plugins

import com.github.manotbatsis.kotlin.utils.api.AnnotationProcessorPlugin
import com.github.manotbatsis.kotlin.utils.kapt.dto.strategy.DtoStrategy
import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo


interface DtoStrategyFactoryProcessorPlugin : AnnotationProcessorPlugin {
    fun buildDtoInputContext(
            annotatedElementInfo: AnnotatedElementInfo,
            dtoStrategyClass: Class<out DtoStrategy>
    ): DtoStrategy
}


open class DefaultDtoStrategyFactoryProcessorPlugin: DtoStrategyFactoryProcessorPlugin {
    override fun buildDtoInputContext(
            annotatedElementInfo: AnnotatedElementInfo,
            dtoStrategyClass: Class<out DtoStrategy>
    ): DtoStrategy {

        val dtoStrategy = dtoStrategyClass
                .getConstructor( AnnotatedElementInfo::class.java )
                .newInstance(annotatedElementInfo)

        return dtoStrategy
    }
}
