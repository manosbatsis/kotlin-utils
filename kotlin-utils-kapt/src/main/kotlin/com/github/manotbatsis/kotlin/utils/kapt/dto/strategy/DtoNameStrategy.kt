package com.github.manotbatsis.kotlin.utils.kapt.dto.strategy

import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName

interface DtoNameStrategy  {
    /** Map input - output package name */
    fun mapPackageName(original: String): String
    /** Override to change the DTO package and class name */
    fun getClassName(): ClassName
    /** Override to change the DTO package and class name */
    fun getClassNameSuffix(): String
}

open class SimpleDtoNameStrategy(
    val annotatedElementInfo: AnnotatedElementInfo
) : DtoNameStrategy, AnnotatedElementInfo by annotatedElementInfo {

    override fun mapPackageName(original: String): String = original

    override fun getClassName(): ClassName {
        val packageName = primaryTargetTypeElement.asClassName().packageName
        val mappedPackageName = mapPackageName(packageName)
        return ClassName(mappedPackageName, "${primaryTargetTypeElement.simpleName}${getClassNameSuffix()}")
    }

    override fun getClassNameSuffix(): String = "Dto"

}
