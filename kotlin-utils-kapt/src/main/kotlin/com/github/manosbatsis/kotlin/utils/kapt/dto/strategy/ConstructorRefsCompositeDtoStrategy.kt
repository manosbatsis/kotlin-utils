package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.DtoMembersStrategy
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.DtoNameStrategy
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.DtoStrategyLesserComposition
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.DtoTypeStrategy
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import kotlin.reflect.KFunction1

/** A [DtoStrategyComposition] that uses constructor references to simplify extensions */
open class ConstructorRefsCompositeDtoStrategy<N : DtoNameStrategy, T : DtoTypeStrategy, M : DtoMembersStrategy>(
        override val annotatedElementInfo: AnnotatedElementInfo,
        val dtoNameStrategyConstructor: KFunction1<AnnotatedElementInfo, N>,
        val dtoTypeStrategyConstructor: KFunction1<AnnotatedElementInfo, T>,
        val dtoMembersStrategyConstructor: KFunction1<DtoStrategyLesserComposition, M>
) : ClonableDtoStrategyComposition {

    override val dtoNameStrategy = dtoNameStrategyConstructor(annotatedElementInfo)
    override val dtoTypeStrategy = dtoTypeStrategyConstructor(annotatedElementInfo)
    override val dtoMembersStrategy by lazy {
        dtoMembersStrategyConstructor(this)
    }

    override fun with(annotatedElementInfo: AnnotatedElementInfo): ConstructorRefsCompositeDtoStrategy<N, T, M> {
        return ConstructorRefsCompositeDtoStrategy(
                annotatedElementInfo = annotatedElementInfo,
                dtoNameStrategyConstructor = dtoNameStrategyConstructor,
                dtoTypeStrategyConstructor = dtoTypeStrategyConstructor,
                dtoMembersStrategyConstructor = dtoMembersStrategyConstructor
        )
    }

}