package com.github.manotbatsis.kotlin.utils.kapt.plugins

import com.github.manotbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo

/** Plugin interface for Annotation Processors */
interface AnnotationProcessorPlugin {

    /** Get the level of support, higher is better, zero is no support */
    fun getSupportPriority(annotatedElementInfo: AnnotatedElementInfo, strategy: String? = null): Int
}
