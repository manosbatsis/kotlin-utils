package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manotbatsis.kotlin.utils.kapt.dto.DtoInputContext
import javax.annotation.processing.ProcessingEnvironment

interface DtoStrategyComposition {
    fun dtoNameStrategy(
            processingEnvironment: ProcessingEnvironment,
            dtoInputContext: DtoInputContext
    ): DtoNameStrategy

    fun dtoMembersStrategy(
            processingEnvironment: ProcessingEnvironment,
            dtoInputContext: DtoInputContext
    ): DtoMembersStrategy

    fun dtoTypeStrategy(
            processingEnvironment: ProcessingEnvironment,
            dtoInputContext: DtoInputContext
    ): DtoTypeStrategy
}

object SimpleDtoStrategyComposition : DtoStrategyComposition {
    override fun dtoNameStrategy(
            processingEnvironment: ProcessingEnvironment,
            dtoInputContext: DtoInputContext
    ): DtoNameStrategy = SimpleDtoNameStrategy(
            processingEnvironment, dtoInputContext
    )

    override fun dtoMembersStrategy(
            processingEnvironment: ProcessingEnvironment,
            dtoInputContext: DtoInputContext
    ): DtoMembersStrategy = SimpleDtoMembersStrategy(
            processingEnvironment, dtoInputContext
    )

    override fun dtoTypeStrategy(
            processingEnvironment: ProcessingEnvironment,
            dtoInputContext: DtoInputContext
    ): DtoTypeStrategy = SimpleDtoTypeStrategy(
            processingEnvironment, dtoInputContext
    )
}