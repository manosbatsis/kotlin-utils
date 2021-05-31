package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.*
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo


/** Delegate-based implementation of [DtoStrategy] */
open class CompositeDtoStrategy(
        override val annotatedElementInfo: AnnotatedElementInfo,
        override val dtoNameStrategy: DtoNameStrategy = SimpleDtoNameStrategy(annotatedElementInfo),
        override val dtoTypeStrategy: DtoTypeStrategy = SimpleDtoTypeStrategy(annotatedElementInfo),
        override val dtoMembersStrategy: DtoMembersStrategy = SimpleDtoMembersStrategy(
                annotatedElementInfo, dtoNameStrategy, dtoTypeStrategy)
) : DtoStrategyComposition {
    constructor(
            annotatedElementInfo: AnnotatedElementInfo,
            composition: DtoStrategyComposition
    ) : this(
            annotatedElementInfo,
            composition.dtoNameStrategy,
            composition.dtoTypeStrategy,
            composition.dtoMembersStrategy
    )
}

