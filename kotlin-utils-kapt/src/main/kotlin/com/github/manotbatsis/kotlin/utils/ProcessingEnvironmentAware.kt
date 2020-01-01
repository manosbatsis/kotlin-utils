 package com.github.manosbatsis.kotlin.utils

 import com.github.manosbatsis.kotlin.utils.api.Dto
 import com.github.manosbatsis.kotlin.utils.api.DtoInsufficientMappingException
 import com.squareup.kotlinpoet.ClassName
 import com.squareup.kotlinpoet.CodeBlock
 import com.squareup.kotlinpoet.FunSpec
 import com.squareup.kotlinpoet.KModifier.DATA
 import com.squareup.kotlinpoet.KModifier.OVERRIDE
 import com.squareup.kotlinpoet.KModifier.PUBLIC
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


data class DtoInfo(
        val originalTypeElement: TypeElement,
        val fields: List<VariableElement>,
        val targetPackage: String,
        val kdoc: CodeBlock? = null
) {
    val originalTypeName by lazy { originalTypeElement.asType().asTypeName() }
}

/**
 * Baee processor implementation.
 */
interface ProcessingEnvironmentAware {

    /** Override to implement [ProcessingEnvironment] access */
    val processingEnvironment: ProcessingEnvironment

    /** Returns all fields in this type that also appear as a constructor parameter. */
    fun TypeElement.accessibleConstructorParameterFields(): List<VariableElement> {
        val allMembers = processingEnvironment.elementUtils.getAllMembers(this)
        val fields = ElementFilter.fieldsIn(allMembers)
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


    fun dtoSpecBuilder(dtoInfo: DtoInfo): TypeSpec.Builder {
        // Create DTO type
        val dtoTypeSpecBuilder = TypeSpec.classBuilder(
                ClassName(dtoInfo.targetPackage, "${dtoInfo.originalTypeElement.simpleName}Dto"))
                .addSuperinterface(Dto::class.asClassName().parameterizedBy(dtoInfo.originalTypeName))
                .addModifiers(DATA)
                .addKdoc("A [%T]-specific [%T] implementation", dtoInfo.originalTypeName, Dto::class)
        // Contract state parameter, used in alt constructor and util functions
        val stateParameter = ParameterSpec.builder("original", dtoInfo.originalTypeName).build()
        // Create DTO primary constructor
        val dtoConstructorBuilder = FunSpec.constructorBuilder()
        // Create DTO alternative constructor
        val dtoAltConstructorBuilder = FunSpec.constructorBuilder().addParameter(stateParameter)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Alternative constructor, used to map ")
                        .addStatement("from the given [%T] instance.", dtoInfo.originalTypeName).build())
        val dtoAltConstructorCodeBuilder = CodeBlock.builder().addStatement("")
        // Create patch function
        val patchFunctionBuilder = FunSpec.builder("toPatched")
                .addModifiers(OVERRIDE)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create a patched copy of the given [%T] instance,", dtoInfo.originalTypeName)
                        .addStatement("updated using this DTO's non-null properties.").build())
                .addParameter(stateParameter)
                .returns(dtoInfo.originalTypeName)
        val patchFunctionCodeBuilder = CodeBlock.builder().addStatement("val patched = %T(", dtoInfo.originalTypeName)
        // Create mapping function
        val toStateFunctionBuilder = FunSpec.builder("toTargetType")
                .addModifiers(OVERRIDE)
                .addKdoc(CodeBlock.builder()
                        .addStatement("Create an instance of [%T], using this DTO's properties.", dtoInfo.originalTypeName)
                        .addStatement("May throw a [DtoInsufficientStateMappingException] ")
                        .addStatement("if there is mot enough information to do so.").build())
                .returns(dtoInfo.originalTypeName)
        val toStateFunctionCodeBuilder = CodeBlock.builder()
                .addStatement("try {")
                .addStatement("   val originalTypeInstance = %T(", dtoInfo.originalTypeName)

        dtoInfo.fields.forEachIndexed { index, variableElement ->
            val commaOrEmpty = if (index + 1 < dtoInfo.fields.size) "," else ""
            // Tell KotlinPoet that the property is initialized via the constructor parameter,
            // by creating both a constructor param and member property
            val propertyName = variableElement.simpleName.toString()
            val propertyType = variableElement.asKotlinTypeName().copy(nullable = true)
            dtoConstructorBuilder.addParameter(ParameterSpec.builder(propertyName, propertyType)
                    .defaultValue("null")
                    .build())
            dtoTypeSpecBuilder.addProperty(PropertySpec.builder(propertyName, propertyType)
                    .mutable()
                    .addModifiers(PUBLIC)
                    .initializer(propertyName).build())
            // Add line to path function
            patchFunctionCodeBuilder.addStatement("      $propertyName = this.$propertyName ?: original.$propertyName$commaOrEmpty")
            // Add line to map function
            val nullableOrNot = if (variableElement.isNullable()) "" else "!!"
            toStateFunctionCodeBuilder.addStatement("      $propertyName = this.$propertyName$nullableOrNot$commaOrEmpty")
            // Add line to alt constructor
            dtoAltConstructorCodeBuilder.addStatement("      $propertyName = original.$propertyName$commaOrEmpty")
        }

        // Complete alt constructor
        dtoAltConstructorBuilder.callThisConstructor(dtoAltConstructorCodeBuilder.build())
        // Complete patch function
        patchFunctionCodeBuilder.addStatement(")")
        patchFunctionCodeBuilder.addStatement("return patched")
        // Complete mappiong function

        toStateFunctionCodeBuilder.addStatement("   )")
        toStateFunctionCodeBuilder.addStatement("   return originalTypeInstance")
        toStateFunctionCodeBuilder.addStatement("}")
        toStateFunctionCodeBuilder.addStatement("catch(e: Exception) {")
        toStateFunctionCodeBuilder.addStatement("   throw %T(exception = e)", DtoInsufficientMappingException::class)
        toStateFunctionCodeBuilder.addStatement("}")

        return dtoTypeSpecBuilder
                .primaryConstructor(dtoConstructorBuilder.build())
                .addFunction(dtoAltConstructorBuilder.build())
                .addFunction(patchFunctionBuilder.addCode(patchFunctionCodeBuilder.build()).build())
                .addFunction(toStateFunctionBuilder.addCode(toStateFunctionCodeBuilder.build()).build())
    }

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

    /** Get the given annotation value as a [TypeElement] if it exists, null otherwise */
    fun AnnotationMirror.findValueAsTypeElement(propertyName: String): TypeElement? {
        val baseFlowAnnotationValue = this.findAnnotationValue(propertyName) ?: return null
        return processingEnvironment.typeUtils.asElement(baseFlowAnnotationValue.value as TypeMirror) as TypeElement?
    }

    /** Get the given annotation value as a [TypeElement] if it exists, throw an error otherwise */
    fun AnnotationMirror.getValueAsTypeElement(propertyName: String): TypeElement =
            this.findValueAsTypeElement(propertyName) ?: throw IllegalStateException("Could not find a valid value for $propertyName")

    /** Get the given annotation value as a [AnnotationValue] if it exists, throw an error otherwise */
    fun AnnotationMirror.getAnnotationValue(name: String): AnnotationValue =
            findAnnotationValue(name) ?: throw IllegalStateException("Annotation value not found for string '$name'")

    /** Get the given annotation value as a [AnnotationValue] if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValue(name: String): AnnotationValue?  =
                processingEnvironment.elementUtils.getElementValuesWithDefaults(this).keys
                .filter { k -> k.simpleName.toString() == name }
                .mapNotNull { k -> elementValues[k] }
                .firstOrNull()

    /** Prints an error message using this element as a position hint. */
    fun Element.errorMessage(message: () -> String) {
        processingEnvironment.messager.printMessage(ERROR, message()+"\n", this)
    }

    fun ProcessingEnvironment.errorMessage(message: () -> String) {
        this.messager.printMessage(ERROR, message()+"\n")
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