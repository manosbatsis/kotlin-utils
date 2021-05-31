package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.ClassName

/** Simple implementation of [DtoNameStrategy] */
open class SimpleDtoNameStrategy(
    val annotatedElementInfo: AnnotatedElementInfo
) : DtoNameStrategy, AnnotatedElementInfo by annotatedElementInfo {

    override fun mapPackageName(original: String): String = original

    override fun getClassName(): ClassName {
        val mappedPackageName = mapPackageName(annotatedElementInfo.generatedPackageName)
        return ClassName(mappedPackageName, "${primaryTargetTypeElement.simpleName}${getClassNameSuffix()}")
    }

    override fun getClassNameSuffix(): String = annotatedElementInfo.overrideClassNameSuffix ?: "Dto"

}