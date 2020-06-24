package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.github.manotbatsis.kotlin.utils.kapt.dto.DtoInputContext
import com.github.manotbatsis.kotlin.utils.kapt.dto.DtoInputContextAware
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import javax.annotation.processing.ProcessingEnvironment

interface DtoNameStrategy : DtoInputContextAware {
    /** Map input - output package name */
    fun mapPackageName(original: String): String

    /** Override to change the DTO package and class name */
    fun getClassName(): ClassName
}

open class SimpleDtoNameStrategy(
        override val processingEnvironment: ProcessingEnvironment,
        override val dtoInputContext: DtoInputContext
) : DtoNameStrategy, ProcessingEnvironmentAware {

    override fun mapPackageName(original: String): String =
            "${original}.generated"

    override fun getClassName() = ClassName(
            mapPackageName(dtoInputContext.originalTypeElement.asClassName().packageName),
            "${dtoInputContext.originalTypeElement.simpleName}Dto")

}