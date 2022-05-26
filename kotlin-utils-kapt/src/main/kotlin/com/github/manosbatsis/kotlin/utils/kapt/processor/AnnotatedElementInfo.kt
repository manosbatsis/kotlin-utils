package com.github.manosbatsis.kotlin.utils.kapt.processor

import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement


interface AnnotatedElementFieldInfo{
    /** The member field wrapped by this instance */
    val variableElement: VariableElement
    /**
     * Whether the member field is in scope of the annotation being processed.
     * For example, a field might be out of scope when the annotated element is a constructor VS a class.
     */
    val isInAnnotationScope: Boolean
    /** Whether the field is considered updatable. Mey be overridden by [AnnotatedElementInfo.nonUpdatableProperties] */
    val isUpdatable: Boolean
    /** Whether the field is mutable, i.e. `var` VS `val` in Kotlin or via setter in Java  */
    val isMutableVariable: Boolean
    /** Whether the field is included in a constructor */
    val isConstructorParam: Boolean
    /** Whether the annotated element including this field name was a constructor */
    val isConstructorSource: Boolean

    val simpleName: String
}

interface AnnotatedElementInfo {
    val processingEnvironment: ProcessingEnvironment
    val annotation: AnnotationMirror
    val primaryTargetTypeElement: TypeElement
    val primaryTargetTypeElementFields: List<AnnotatedElementFieldInfo>
    val secondaryTargetTypeElement: TypeElement?
    val secondaryTargetTypeElementFields: List<AnnotatedElementFieldInfo>
    val mixinTypeElement: TypeElement?
    val mixinTypeElementFields: List<AnnotatedElementFieldInfo>
    val copyAnnotationPackages: List<String>
    val ignoreProperties: List<String>
    val nonUpdatableProperties: List<String>
    val generatedPackageName: String
    val sourceRoot: File
    val primaryTargetTypeElementSimpleName: String
    val secondaryTargetTypeElementSimpleName: String?
    val mixinTypeElementSimpleName: String?
    var overrideClassNameSuffix: String?
    var overrideClassName: String?
    var overrideDtoInterface: Class<*>?
    val toTargetTypeFunctionConfig: ToTargetTypeFunctionConfig
    val isNonDataClass: Boolean
    val updateRequiresNewInstance: Boolean

    fun isUpdatable(fieldInfo: AnnotatedElementFieldInfo): Boolean =
        fieldInfo.isUpdatable && !nonUpdatableProperties.contains(fieldInfo.simpleName)
}

data class ToTargetTypeFunctionConfig(
        val skip: Boolean = false,
        val patchStatements: Boolean = false,
        val name: String = "toTargetType",
        val params: List<ParameterSpec> = emptyList(),
        val targetTypeNameOverride: TypeName? = null
)

data class SimpleAnnotatedElementFieldInfo(
    override val variableElement: VariableElement,
    override val isInAnnotationScope: Boolean,
    override val isUpdatable: Boolean,
    override val isMutableVariable: Boolean,
    override val isConstructorParam: Boolean,
    override val isConstructorSource: Boolean
): AnnotatedElementFieldInfo {
    override val simpleName: String
        get() = variableElement.simpleName.toString()
}


data class SimpleAnnotatedElementInfo(
    override val processingEnvironment: ProcessingEnvironment,
    override val annotation: AnnotationMirror,
    override val primaryTargetTypeElement: TypeElement,
    override val primaryTargetTypeElementFields: List<AnnotatedElementFieldInfo>,
    override val secondaryTargetTypeElement: TypeElement?,
    override val secondaryTargetTypeElementFields: List<AnnotatedElementFieldInfo>,
    override val mixinTypeElement: TypeElement?,
    override val mixinTypeElementFields: List<AnnotatedElementFieldInfo>,
    override val copyAnnotationPackages: List<String>,
    override val ignoreProperties: List<String>,
    override val nonUpdatableProperties: List<String>,
    override val generatedPackageName: String,
    override val sourceRoot: File,
    override val primaryTargetTypeElementSimpleName: String = primaryTargetTypeElement.simpleName.toString(),
    override val secondaryTargetTypeElementSimpleName: String? = secondaryTargetTypeElement?.simpleName.toString(),
    override val mixinTypeElementSimpleName: String? = mixinTypeElement?.simpleName.toString(),
    override var overrideClassNameSuffix: String? = null,
    override var overrideClassName: String? = null,
    override var overrideDtoInterface: Class<*>? = null,
    override val toTargetTypeFunctionConfig: ToTargetTypeFunctionConfig = ToTargetTypeFunctionConfig(),
    override val isNonDataClass: Boolean

) : AnnotatedElementInfo{

    init {
        if(primaryTargetTypeElementSimpleName == secondaryTargetTypeElementSimpleName)
            throw IllegalArgumentException("Primary and secondary target types cannot be the same")
    }

    override val updateRequiresNewInstance: Boolean by lazy {
        primaryTargetTypeElementFields.find {
            it.isInAnnotationScope && !it.isMutableVariable
        } != null
    }
}
