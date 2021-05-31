package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.squareup.kotlinpoet.ClassName

/**
 * Used by [DtoStrategyComposition] implementations to delegate
 * processing related to the generated DTO name.
 */
interface DtoNameStrategy {
    /** Map input - output package name */
    fun mapPackageName(original: String): String

    /** Override to change the DTO package and class name */
    fun getClassName(): ClassName

    /** Override to change the DTO package and class name */
    fun getClassNameSuffix(): String
}

