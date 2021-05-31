package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.DtoStrategyComposition
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.SimpleDtoMembersStrategy
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.SimpleDtoNameStrategy
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.SimpleDtoTypeStrategy
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo

/** A simple [DtoStrategyComposition] implementation */
@Deprecated("Use CompositeDtoStrategy or ConstructorRefsCompositeDtoStrategy")
open class SimpleDtoStrategyComposition(
        override val annotatedElementInfo: AnnotatedElementInfo
) : DtoStrategyComposition {
    override val dtoNameStrategy = SimpleDtoNameStrategy(annotatedElementInfo)
    override val dtoTypeStrategy = SimpleDtoTypeStrategy(annotatedElementInfo)
    override val dtoMembersStrategy = SimpleDtoMembersStrategy(
            annotatedElementInfo, dtoNameStrategy, dtoTypeStrategy
    )
}