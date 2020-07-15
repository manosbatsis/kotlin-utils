package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo

interface DtoStrategyComposition {
    fun dtoNameStrategy(
            annotatedElementInfo: AnnotatedElementInfo
    ): DtoNameStrategy

    fun dtoMembersStrategy(
            annotatedElementInfo: AnnotatedElementInfo
    ): DtoMembersStrategy

    fun dtoTypeStrategy(
            annotatedElementInfo: AnnotatedElementInfo
    ): DtoTypeStrategy
}

open class SimpleDtoStrategyComposition : DtoStrategyComposition {
    override fun dtoNameStrategy(
            annotatedElementInfo: AnnotatedElementInfo
    ): DtoNameStrategy = SimpleDtoNameStrategy(
            annotatedElementInfo
    )

    override fun dtoMembersStrategy(
            annotatedElementInfo: AnnotatedElementInfo
    ): DtoMembersStrategy = SimpleDtoMembersStrategy(
            annotatedElementInfo
    )

    override fun dtoTypeStrategy(annotatedElementInfo: AnnotatedElementInfo
    ): DtoTypeStrategy = SimpleDtoTypeStrategy(annotatedElementInfo)
}
