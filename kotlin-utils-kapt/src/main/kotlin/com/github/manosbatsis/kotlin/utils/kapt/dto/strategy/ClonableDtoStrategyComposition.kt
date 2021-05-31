package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.DtoStrategyComposition
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo

/** A strategy that can be cloned for use with another [annotatedElementInfo] */
interface ClonableDtoStrategyComposition : DtoStrategyComposition {
    /** Clones the current instance using a new [annotatedElementInfo] */
    fun with(annotatedElementInfo: AnnotatedElementInfo): ClonableDtoStrategyComposition
}