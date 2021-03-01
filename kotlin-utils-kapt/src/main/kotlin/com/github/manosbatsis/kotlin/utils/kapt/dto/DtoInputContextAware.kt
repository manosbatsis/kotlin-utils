package com.github.manosbatsis.kotlin.utils.kapt.dto

interface DtoInputContextAware {

    /** The [DtoInputContext] that created ths instance */
    val dtoInputContext: DtoInputContext
}