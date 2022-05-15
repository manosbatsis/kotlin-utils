package com.github.manosbatsis.kotlin.utils.kapt.processor

import com.github.manosbatsis.kotlin.utils.ProcessingEnvironmentAware
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asTypeName
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.*
import javax.lang.model.util.ElementFilter

interface AnnotatedElementInfoProcessor : AnnotationProcessorBase{
    fun processElementInfos(elementInfos: List<AnnotatedElementInfo>)
}
interface AnnotationProcessorBase: ProcessingEnvironmentAware{

    companion object {
        const val ANN_ATTR_CIGNORE_PROPS = "ignoreProperties"
        const val ANN_ATTR_COPY_ANNOTATION_PACKAGES = "copyAnnotationPackages"
        const val BLOCK_FUN_NAME = "block"
        const val KAPT_OPTION_NAME_KAPT_KOTLIN_GENERATED = "kapt.kotlin.generated"
        const val KAPT_OPTION_OVERRIDE__NAME_KAPT_KOTLIN_GENERATED = "override.kapt.kotlin.vaultaire.generated"

        val TYPE_PARAMETER_STAR = WildcardTypeName.producerOf(Any::class.asTypeName().copy(nullable = true))
    }

    val primaryTargetRefAnnotationName: String?
    val secondaryTargetRefAnnotationName: String?

    val generatedSourcesRoot: String
    val sourceRootFile: File

    /**
     * Maps input to generated package name.
     * The default implementation calls [mapPackageName(javax.lang.model.element.TypeElement)]
     * passing the [mixinTypeElement] if not null or the [primaryTargetTypeElement] otherwise.
     */
    fun mapPackageName(
            primaryTargetTypeElement: TypeElement,
            secondaryTargetTypeElement: TypeElement?,
            mixinTypeElement: TypeElement?) = mapPackageName(mixinTypeElement ?: primaryTargetTypeElement)

    /**
     * Maps input to generated package name.
     * The default implementation calls [mapPackageName(java.lang.String)]
     * passing the [sourceTypeElement] package name as a string
     */
    fun mapPackageName(sourceTypeElement: TypeElement) = mapPackageName(
            processingEnvironment.elementUtils.getPackageOf(sourceTypeElement).toString()
    )

    /**
     * Maps input to generated package name.
     * The default implementation is an identity mapping, i.e. returns the input as-is.
     */
    fun mapPackageName(sourcePackageName: String) = sourcePackageName

    fun annotatedElementInfo(

            annotation: AnnotationMirror,
            primaryTargetTypeElement: TypeElement,
            primaryTargetTypeElementFields: List<VariableElement>,
            secondaryTargetTypeElement: TypeElement?,
            secondaryTargetTypeElementFields: List<VariableElement>,
            mixinTypeElement: TypeElement?,
            mixinTypeElementFields: List<VariableElement>,
            copyAnnotationPackages: List<String> = getStringValuesList(annotation, ANN_ATTR_COPY_ANNOTATION_PACKAGES),
            ignoreProperties: List<String> = getStringValuesList(annotation, ANN_ATTR_CIGNORE_PROPS),
            nonUpdatableProperties: List<String> = emptyList(),
            sourceRoot: File,
            generatedPackageName: String = mapPackageName(primaryTargetTypeElement, secondaryTargetTypeElement, mixinTypeElement)

    ): AnnotatedElementInfo

    fun toAnnotatedElements(
            roundEnv: RoundEnvironment,
            supportedAnnotationTypes: Set<String>
    ): Map<Class<out Annotation>, Set<Element>> {
        return supportedAnnotationTypes
                .map { Class.forName(it) as Class<out Annotation> }
                .map { it to roundEnv.getElementsAnnotatedWith(it).toSet() }
                .toMap()
    }

    fun getFieldNameExclusions(): Set<String> = emptySet()

    fun toAnnotatedElementInfos(
            annotatedElements: Map<Class<out Annotation>, Set<out Element>>
    ): List<AnnotatedElementInfo> {
        val classAndInterfaceKinds = listOf(ElementKind.CLASS, ElementKind.INTERFACE)
        val fieldNameExclusions = getFieldNameExclusions()
        return annotatedElements.flatMap { annotationGroup ->

            annotationGroup.value.map { annotatedElement ->
                val annotation = annotatedElement.getAnnotationMirror(annotationGroup.key)
                when {
                    classAndInterfaceKinds.contains(annotatedElement.kind) -> {
                        val typeElement = annotatedElement as TypeElement
                        when {
                            isMixinAnnotation(annotationGroup.key.canonicalName) -> annotatedElementInfoForMixin(
                                    mixinTypeElement = typeElement,
                                    mixinTypeElementFields = typeElement.accessibleConstructorParameterFields(true)
                                            .filterNot { fieldNameExclusions.contains("${it.simpleName}") },
                                    annotation = annotation)
                            else -> annotatedElementInfo(
                                    primaryTargetTypeElement = typeElement,
                                    primaryTargetTypeElementFields = typeElement.accessibleConstructorParameterFields(true)
                                            .filterNot { fieldNameExclusions.contains("${it.simpleName}") },
                                    annotation = annotation)
                        }
                    }
                    ElementKind.CONSTRUCTOR == annotatedElement.kind -> {
                        val executableElement = annotatedElement as ExecutableElement
                        if (isMixinAnnotation(annotationGroup.key.canonicalName)) annotatedElementInfoForMixin(
                                mixinTypeElement = executableElement.enclosingElement as TypeElement,
                                mixinTypeElementFields = executableElement.parameters
                                        .filterNot { fieldNameExclusions.contains("${it.simpleName}") },
                                annotation = annotation)
                        else annotatedElementInfo(
                                primaryTargetTypeElement = executableElement.enclosingElement as TypeElement,
                                primaryTargetTypeElementFields = executableElement.parameters
                                        .filterNot { fieldNameExclusions.contains("${it.simpleName}") },
                                annotation = annotation)
                    }
                    else -> throw IllegalArgumentException("Invalid element type, expected a class or constructor")
                }
            }
        }
    }

    fun isMixinAnnotation(annotation: Class<Annotation>) = isMixinAnnotation(annotation.simpleName)
    fun isMixinAnnotation(name: String): Boolean = primaryTargetRefAnnotationName != null
            && (name.endsWith("Mixin") || name.endsWith("ForDependency"))

    fun annotatedElementInfo(
            primaryTargetTypeElement: TypeElement,
            primaryTargetTypeElementFields: List<VariableElement>,
            mixinTypeElement: TypeElement? = null,
            mixinTypeElementFields: List<VariableElement> = emptyList(),
            annotation: AnnotationMirror
    ): AnnotatedElementInfo {

        val secondaryTargetTypeElement: TypeElement? = secondaryTargetRefAnnotationName?.let {
            annotation.findValueAsTypeElement(it)
        }

        val secondaryTargetTypeElementFields = if (secondaryTargetTypeElement != null) ElementFilter.fieldsIn(
                processingEnvironment.elementUtils.getAllMembers(secondaryTargetTypeElement)).fieldsOnly()
                .filterNot { getFieldNameExclusions().contains("${it.simpleName}") } else emptyList()

        return annotatedElementInfo(
                annotation = annotation,
                primaryTargetTypeElement = primaryTargetTypeElement,
                primaryTargetTypeElementFields = primaryTargetTypeElementFields,
                secondaryTargetTypeElement = secondaryTargetTypeElement,
                secondaryTargetTypeElementFields = secondaryTargetTypeElementFields,
                mixinTypeElement = mixinTypeElement,
                mixinTypeElementFields = mixinTypeElementFields,
                sourceRoot = sourceRootFile)
    }

    fun annotatedElementInfoForMixin(
            mixinTypeElement: TypeElement,
            mixinTypeElementFields: List<VariableElement>,
            annotation: AnnotationMirror
    ): AnnotatedElementInfo {
        val annotationTargetTypeAttr = primaryTargetRefAnnotationName ?: error("Not a mixin")
        val primaryTargetTypeElement: TypeElement = annotation.getValueAsTypeElement(annotationTargetTypeAttr)
        val primaryTargetTypeElementFields = ElementFilter.fieldsIn(
                processingEnvironment.elementUtils.getAllMembers(primaryTargetTypeElement)).fieldsOnly()
                .filterNot { getFieldNameExclusions().contains("${it.simpleName}") }
        return annotatedElementInfo(
                annotation = annotation,
                primaryTargetTypeElement = primaryTargetTypeElement,
                primaryTargetTypeElementFields = primaryTargetTypeElementFields,
                mixinTypeElement = mixinTypeElement,
                mixinTypeElementFields = mixinTypeElementFields
        )
    }

}


abstract class AbstractAnnotatedModelInfoProcessor(
        override val primaryTargetRefAnnotationName: String?,
        override val secondaryTargetRefAnnotationName: String?
): AbstractProcessor(), AnnotatedElementInfoProcessor{

    /** Implement [ProcessingEnvironment] access */
    override val processingEnvironment by lazy {
        processingEnv
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements =
                toAnnotatedElements(roundEnv, supportedAnnotationTypes)
        // Return if there's nothing to process, go ahead otherwise
        if (annotatedElements.isEmpty()) return false
        processElementInfos(toAnnotatedElementInfos(annotatedElements))
        return false
    }

    override val generatedSourcesRoot: String by lazy {
        processingEnvironment.options[AnnotationProcessorBase.KAPT_OPTION_OVERRIDE__NAME_KAPT_KOTLIN_GENERATED]
                ?: processingEnvironment.options[AnnotationProcessorBase.KAPT_OPTION_NAME_KAPT_KOTLIN_GENERATED]
                ?: throw IllegalStateException("Can't find the target directory for generated Kotlin files.")
    }

    override val sourceRootFile by lazy {
        val sourceRootFile = File(generatedSourcesRoot)
        sourceRootFile.mkdir()
        sourceRootFile
    }

    override fun annotatedElementInfo(
            annotation: AnnotationMirror,
            primaryTargetTypeElement: TypeElement,
            primaryTargetTypeElementFields: List<VariableElement>,
            secondaryTargetTypeElement: TypeElement?,
            secondaryTargetTypeElementFields: List<VariableElement>,
            mixinTypeElement: TypeElement?,
            mixinTypeElementFields: List<VariableElement>,
            copyAnnotationPackages: List<String>,
            ignoreProperties: List<String>,
            nonUpdatableProperties: List<String>,
            sourceRoot: File,
            generatedPackageName: String

    ) = SimpleAnnotatedElementInfo(
            processingEnvironment = processingEnvironment,
            annotation = annotation,
            primaryTargetTypeElement = primaryTargetTypeElement,
            primaryTargetTypeElementFields = primaryTargetTypeElementFields,
            secondaryTargetTypeElement = secondaryTargetTypeElement,
            secondaryTargetTypeElementFields = secondaryTargetTypeElementFields,
            mixinTypeElement = mixinTypeElement,
            mixinTypeElementFields = mixinTypeElementFields,
            copyAnnotationPackages = copyAnnotationPackages,
            ignoreProperties = ignoreProperties,
            nonUpdatableProperties = nonUpdatableProperties,
            sourceRoot = sourceRootFile,
            generatedPackageName = generatedPackageName,
            isNonDataClass = annotation.findAnnotationValue("nonDataClass")?.value as Boolean?
                    ?: false
    )


}

