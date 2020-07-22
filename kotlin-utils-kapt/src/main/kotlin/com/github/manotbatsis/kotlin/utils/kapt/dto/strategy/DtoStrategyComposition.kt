package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo

interface DtoStrategyComposition {
    val annotatedElementInfo: AnnotatedElementInfo
    val dtoNameStrategy: DtoNameStrategy
    val dtoTypeStrategy: DtoTypeStrategy
    val dtoMembersStrategy: DtoMembersStrategy

}

open class SimpleDtoStrategyComposition(
        override val annotatedElementInfo: AnnotatedElementInfo
) : DtoStrategyComposition {

    override val dtoNameStrategy = SimpleDtoNameStrategy(annotatedElementInfo)
    override val dtoTypeStrategy = SimpleDtoTypeStrategy(annotatedElementInfo)
    override val dtoMembersStrategy = SimpleDtoMembersStrategy(
            annotatedElementInfo, dtoNameStrategy, dtoTypeStrategy
    )
}
