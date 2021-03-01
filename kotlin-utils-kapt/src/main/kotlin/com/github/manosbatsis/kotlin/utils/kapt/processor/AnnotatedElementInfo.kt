package com.github.manosbatsis.kotlin.utils.kapt.processor

import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement


interface AnnotatedElementInfo {
    val processingEnvironment: ProcessingEnvironment
    val annotation: AnnotationMirror
    val primaryTargetTypeElement: TypeElement
    val primaryTargetTypeElementFields: List<VariableElement>
    val secondaryTargetTypeElement: TypeElement?
    val secondaryTargetTypeElementFields: List<VariableElement>
    val mixinTypeElement: TypeElement?
    val mixinTypeElementFields: List<VariableElement>
    val copyAnnotationPackages: List<String>
    val ignoreProperties: List<String>
    val generatedPackageName: String
    val sourceRoot: File
    val primaryTargetTypeElementSimpleName: String
    val secondaryTargetTypeElementSimpleName: String?
    val mixinTypeElementSimpleName: String?
}

data class SimpleAnnotatedElementInfo(
        override val processingEnvironment: ProcessingEnvironment,
        override val annotation: AnnotationMirror,
        override val primaryTargetTypeElement: TypeElement,
        override val primaryTargetTypeElementFields: List<VariableElement>,
        override val secondaryTargetTypeElement: TypeElement?,
        override val secondaryTargetTypeElementFields: List<VariableElement>,
        override val mixinTypeElement: TypeElement?,
        override val mixinTypeElementFields: List<VariableElement>,
        override val copyAnnotationPackages: List<String>,
        override val ignoreProperties: List<String>,
        override val generatedPackageName: String,
        override val sourceRoot: File,
        override val primaryTargetTypeElementSimpleName: String =  primaryTargetTypeElement.simpleName.toString(),
        override val secondaryTargetTypeElementSimpleName: String? =  secondaryTargetTypeElement?.simpleName.toString(),
        override val mixinTypeElementSimpleName: String? =  mixinTypeElement?.simpleName.toString()
) : AnnotatedElementInfo{
    init {
        if(primaryTargetTypeElementSimpleName == secondaryTargetTypeElementSimpleName)
            throw IllegalArgumentException("Primary and secondary target types cannot be the same")
    }
}
