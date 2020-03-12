 package com.github.manosbatsis.kotlin.utils

 import com.github.manotbatsis.kotlin.utils.dto.DtoTypeSpecBuilder
 import com.squareup.kotlinpoet.AnnotationSpec
 import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
 import com.squareup.kotlinpoet.ClassName
 import com.squareup.kotlinpoet.FunSpec
 import com.squareup.kotlinpoet.ParameterSpec
 import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
 import com.squareup.kotlinpoet.PropertySpec
 import com.squareup.kotlinpoet.TypeName
 import com.squareup.kotlinpoet.TypeSpec
 import com.squareup.kotlinpoet.asClassName
 import com.squareup.kotlinpoet.asTypeName
import org.jetbrains.annotations.NotNull
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.NOTE


 /**
 * Baee processor implementation.
 */
interface ProcessingEnvironmentAware {

    /** Override to implement [ProcessingEnvironment] access */
    val processingEnvironment: ProcessingEnvironment

    /** Returns all fields in this type that also appear as a constructor parameter. */
    fun TypeElement.accessibleConstructorParameterFields(): List<VariableElement> {
        val allMembers = processingEnvironment.elementUtils.getAllMembers(this)
        val fields = ElementFilter.fieldsIn(allMembers).filterNot { it.kind.isClass || it.kind.isInterface }
        val constructors = ElementFilter.constructorsIn(allMembers)
        val constructorParamNames = constructors
                .flatMap { it.parameters }
                .filterNotNull()
                .filterNot {
                    it.modifiers.contains(Modifier.PRIVATE)
                            || it.modifiers.contains(Modifier.PROTECTED)
                }
                .map { it.simpleName.toString() }
                .toSet()
        return fields.filter { constructorParamNames.contains(it.simpleName.toString()) }
    }

    fun Iterable<String>.hasBasePackageOf(packageName: String): Boolean {
        this.forEach { basePkg ->
            if (packageName.startsWith(basePkg)) return true
        }
        return false
    }

     fun filterAnnotationsByBasePackage(source: Element, basePackages: Iterable<String>): List<AnnotationMirror> {
         return source.annotationMirrors.filter { annotationMirror ->
             val annotationPackage = annotationMirror.annotationType.asTypeElement().getPackageName()
             val match = basePackages.hasBasePackageOf(annotationPackage)
             match
         }
     }

     /** A constructor property parameter */
     class ConstructorProperty(val propertySpec: PropertySpec, val defaultValue: String? = null)

     /** Create a constructor with property parameters */
     fun TypeSpec.Builder.primaryConstructor(vararg properties: PropertySpec): TypeSpec.Builder =
             this.primaryConstructor(*properties.map { ConstructorProperty(it) }.toTypedArray())

     /** Create a constructor with property parameters */
     fun TypeSpec.Builder.primaryConstructor(vararg properties: ConstructorProperty): TypeSpec.Builder {
         val propertySpecs = properties.map { it.propertySpec.toBuilder().initializer(it.propertySpec.name).build() }
         val parameters = properties.map {
             val paramSpec = ParameterSpec.builder(it.propertySpec.name, it.propertySpec.type)
             if (it.defaultValue != null) paramSpec.defaultValue(it.defaultValue)
             paramSpec.build()
         }
         val constructor = FunSpec.constructorBuilder()
                 .addParameters(parameters)
                 .build()

         return this
                 .primaryConstructor(constructor)
                 .addProperties(propertySpecs)
     }

     fun TypeSpec.Builder.copyAnnotationsByBasePackage(source: Element, basePackages: Iterable<String>): TypeSpec.Builder {
         filterAnnotationsByBasePackage(source, basePackages).forEach {
             this.addAnnotation(AnnotationSpec.get(it))
         }
         return this
     }

     fun PropertySpec.Builder.copyAnnotationsByBasePackage(source: Element, basePackages: Iterable<String>, siteTarget: UseSiteTarget? = null): PropertySpec.Builder {
         filterAnnotationsByBasePackage(source, basePackages).forEach {
            this.addAnnotation(AnnotationSpec.get(it).toBuilder().useSiteTarget(siteTarget).build())
        }
        return this
    }

     fun dtoSpec(dtoInfo: DtoTypeSpecBuilder): TypeSpec = dtoInfo.dtoStrategy.dtoTypeSpec()

    /**
     * Converts this element to a [TypeName], ensuring that Java types
     * such as [java.lang.String] are converted to their Kotlin equivalent.
     */
    fun Element.asKotlinTypeName(): TypeName = asType().asKotlinTypeName()

    /**
     * Converts this element to a [TypeName], ensuring that Java types
     * such as [java.lang.String] are converted to their Kotlin equivalent.
     */
    fun VariableElement.asKotlinTypeName(): TypeName {
        val typeName = asType().asKotlinTypeName()
        return if (this.isNullable()) typeName.copy(nullable = true) else typeName
    }

    /** Converts this TypeMirror to a [TypeName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent. */
    fun TypeMirror.asKotlinTypeName(): TypeName {
        return when (this) {
            is PrimitiveType -> processingEnvironment.typeUtils.boxedClass(this as PrimitiveType?).asKotlinClassName()
            is ArrayType -> {
                return ClassName("kotlin", "Array")
                        .parameterizedBy(this.componentType.asKotlinTypeName())
            }
            is DeclaredType -> {
                val typeName = this.asTypeElement().asKotlinClassName()
                return if (!this.typeArguments.isEmpty())
                    typeName.parameterizedBy(*typeArguments
                            .mapNotNull { it.asKotlinTypeName() }
                            .toTypedArray())
                else typeName
            }
            else -> this.asTypeName()
        }
    }

    /** Converts this element to a [ClassName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent. */
    fun TypeElement.asKotlinClassName(): ClassName {
        val className = asClassName()
        return try {
            // ensure that java.lang.* and java.util.* etc classes are converted to their kotlin equivalents
            Class.forName(className.canonicalName).kotlin.asClassName()
        } catch (e: ClassNotFoundException) {
            // probably part of the same source tree as the annotated class
            className
        }
    }

    /** Returns the [TypeElement] represented by this [TypeMirror]. */
    fun TypeMirror.asTypeElement(): TypeElement = processingEnvironment.typeUtils.asElement(this) as TypeElement

    /** Returns true if this element is assignable to the given class, false othjerwise */
    fun Element.isAssignableTo(superType: Class<*>): Boolean {
        val superTypeMirror: TypeMirror = processingEnvironment.elementUtils.getTypeElement(superType.canonicalName).asType()
        return processingEnvironment.typeUtils.isAssignable(this.asType(), superTypeMirror)
    }

    /** Returns true as long as this [Element] is not a [PrimitiveType] and does not have the [NotNull] core. */
    fun Element.isNullable(): Boolean {
        if (this.asType() is PrimitiveType) {
            return false
        }
        return !hasAnnotation(NotNull::class.java)
    }

    /**
     * Returns true if this element has the specified [annotation], or if the parent class has a matching constructor parameter with the core.
     * (This is necessary because builder annotations can be applied to both fields and constructor parameters - and constructor parameters take precedence.
     * Rather than require clients to specify, for instance, `@field:CordaoNullableType`, this method also checks for annotations of constructor parameters
     * when this element is a field).
     */
    fun Element.hasAnnotation(annotation: Class<*>): Boolean {
        return hasAnnotationDirectly(annotation) || hasAnnotationViaConstructorParameter(annotation)
    }

    /** Return true if this element has the specified [annotation]. */
    fun Element.hasAnnotationDirectly(annotation: Class<*>): Boolean {
        return this.annotationMirrors
                .mapNotNull { it.annotationType.toString() }
                .toSet()
                .contains(annotation.name)
    }

    /** Return true if there is a constructor parameter with the same name as this element that has the specified [annotation]. */
    fun Element.hasAnnotationViaConstructorParameter(annotation: Class<*>): Boolean {
        val parameterAnnotations = getConstructorParameter()?.annotationMirrors ?: listOf()
        return parameterAnnotations
                .mapNotNull { it.annotationType.toString() }
                .toSet()
                .contains(annotation.name)
    }

    /** Returns the first constructor parameter with the same name as this element, if any such exists. */
    fun Element.getConstructorParameter(): VariableElement? {
        val enclosingElement = this.enclosingElement
        return if (enclosingElement is TypeElement)
            ElementFilter.constructorsIn(processingEnvironment.elementUtils.getAllMembers(enclosingElement))
                    .flatMap { it.parameters }
                    .filterNotNull()
                    .firstOrNull { it.simpleName == this.simpleName }
        else null
    }

    /** Get the mirror of the single annotation instance matching the given [annotationClass] for this element. */
    fun Element.getAnnotationMirror(annotationClass: Class<out Annotation>): AnnotationMirror =
            findAnnotationMirror(annotationClass)
                    ?: throw IllegalStateException("Annotation value not found for class ${annotationClass.name}")

    /** Get the mirror of the single annotation instance matching the given [annotationClass] for this element. */
    fun Element.findAnnotationMirror(annotationClass: Class<out Annotation>): AnnotationMirror? =
            findAnnotationMirrors(annotationClass).firstOrNull()

    /**
     * Get the mirrors of the annotation instances matching the given [annotationClass] for this element.
     * Mostly useful with [Repeatable] annotations.
     */
    fun Element.findAnnotationMirrors(annotationClass: Class<out Annotation>): List<AnnotationMirror> {
        val annotationClassName = annotationClass.name
        return this.annotationMirrors
                .filter { mirror -> mirror != null && mirror.annotationType.toString().equals(annotationClassName) }
    }

    /** Get the package name of this [TypeElement] */
    fun TypeElement.getPackageName(): String {
        return this.asKotlinClassName().topLevelClassName().packageName
    }

    /** Get the given annotation's value as a [TypeElement] if it exists, throw an error otherwise */
    fun Element.getAnnotationValueAsTypeElement(annotation: Class<out Annotation>, propertyName: String): TypeElement? =
            this.findAnnotationValueAsTypeElement(annotation, propertyName)
                    ?: throw IllegalStateException("Could not find a valid value for $propertyName")

    /** Get the given annotation's value as a [TypeElement] if it exists, null otherwise */
    fun Element.findAnnotationValueAsTypeElement(annotation: Class<out Annotation>, propertyName: String): TypeElement? =
            this.findAnnotationMirror(annotation)?.findValueAsTypeElement(propertyName)

    /** Get the given annotation's value if it exists, null otherwise */
    fun Element.findAnnotationValue(annotation: Class<out Annotation>, propertyName: String): AnnotationValue? =
            this.findAnnotationMirror(annotation)?.findAnnotationValue(propertyName)

    /** Get the given annotation value as a [TypeElement] if it exists, null otherwise */
    fun AnnotationMirror.findValueAsTypeElement(propertyName: String): TypeElement? {
        val annotationMirrorValue = this.findValueAsTypeMirror(propertyName) ?: return null
        return processingEnvironment.typeUtils.asElement(annotationMirrorValue) as TypeElement?
    }

    /** Get the given annotation value as a [TypeElement] if it exists, null otherwise */
    fun AnnotationMirror.findValueAsTypeMirror(propertyName: String): TypeMirror? {
        val baseFlowAnnotationValue = this.findAnnotationValue(propertyName) ?: return null
        return baseFlowAnnotationValue.value as TypeMirror
    }

    /** Get the given annotation value as a [TypeElement] if it exists, throw an error otherwise */
    fun AnnotationMirror.getValueAsTypeElement(propertyName: String): TypeElement =
            this.findValueAsTypeElement(propertyName)
                    ?: throw IllegalStateException("Could not find a valid value for $propertyName")

    /** Get the given annotation value as a [AnnotationValue] if it exists, throw an error otherwise */
    fun AnnotationMirror.getAnnotationValue(name: String): AnnotationValue =
            findAnnotationValue(name) ?: throw IllegalStateException("Annotation value not found for string '$name'")

    /** Get the given annotation value as a [AnnotationValue] if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValue(name: String): AnnotationValue? =
            processingEnvironment.elementUtils.getElementValuesWithDefaults(this).keys
                    .filter { k -> k.simpleName.toString() == name }
                    .mapNotNull { k -> elementValues[k] }
                    .firstOrNull()

    /** Get the given annotation value as a list of [AnnotationValue] if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValueList(memberName: String): List<AnnotationValue>? =
            processingEnvironment.elementUtils.getElementValuesWithDefaults(this).entries
                    .filter { it.key.simpleName.toString() == memberName }
                    .mapNotNull { it.value.value }
                    .firstOrNull() as List<AnnotationValue>?

    /** Prints an error message using this element as a position hint. */
    fun Element.errorMessage(message: () -> String) {
        processingEnvironment.messager.printMessage(ERROR, message() + "\n", this)
    }

    fun ProcessingEnvironment.errorMessage(message: () -> String) {
        this.messager.printMessage(ERROR, message() + "\n")
    }

    fun ProcessingEnvironment.noteMessage(message: () -> String) {
        this.messager.printMessage(NOTE, message()+"\n")
    }

    fun <T : Any> T.accessField(fieldName: String): Any? {
        return this.javaClass.getDeclaredField(fieldName).let { field ->
            field?.isAccessible = true
            return@let field?.get(this)
        }
    }
}