package com.github.manosbatsis.kotlin.utils

import com.github.manosbatsis.kotlin.utils.api.NoUpdate
import com.github.manosbatsis.kotlin.utils.kapt.dto.DtoInputContext
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition.DtoMembersStrategy.Statement
import com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util.GetterAsFieldAdapter
import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementFieldInfo
import com.github.manosbatsis.kotlin.utils.kapt.processor.SimpleAnnotatedElementFieldInfo
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.annotations.NotNull
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.*
import javax.lang.model.element.ElementKind.FIELD
import javax.lang.model.type.*
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.NOTE
import kotlin.reflect.KClass


/**
 * Baee processor implementation.
 */
interface ProcessingEnvironmentAware {
    companion object {
        val camelToUnderscorePattern = Pattern.compile("(?<=[a-z])[A-Z]")
        val metaDataClass = Class.forName("kotlin.Metadata").asSubclass(Annotation::class.java)
    }

    /** Override to implement [ProcessingEnvironment] access */
    val processingEnvironment: ProcessingEnvironment

    fun isKotlinClass(el: TypeElement) = el.annotationMirrors.any { it.annotationType.toString() == "kotlin.Metadata" }

    /**
     * https://stackoverflow.com/a/50975195/1309260
     * Check for Java static or Kotlin singleton.
     * An imperfect heuristic: if not static, checks for a static INSTANCE field.
     */
    fun isStatic(element: Element): Boolean {
        if (element.modifiers.contains(Modifier.STATIC)) return true
        else {
            val parent = element.enclosingElement
            if (parent is TypeElement && isKotlinClass(parent)) {
                val instances = parent.enclosedElements
                    .filter { "INSTANCE" == it.simpleName.toString() }
                    .filter { it.modifiers.contains(Modifier.STATIC) }
                    .filter { it.kind.isField }
                return instances.isNotEmpty()
            }
            return false
        }
    }

    fun List<VariableElement>.fieldsOnly() = this.filterNot { it.kind != FIELD || isStatic(it) }

    fun Element.isGetter(): Boolean = (this is ExecutableElement
            && ElementKind.METHOD == kind
            && parameters.isEmpty())
            && ((simpleName.startsWith("get") && "$simpleName" != "getClass")
            || (simpleName.startsWith("is") && returnType.kind == TypeKind.BOOLEAN))

    fun Element.isSetter(): Boolean = (this is ExecutableElement
            && ElementKind.METHOD == kind
            && parameters.size == 1
            && returnType.kind == TypeKind.VOID
            && simpleName.startsWith("set"))

    fun TypeElement.getTypeElementHierarchy(): List<TypeElement> {
        val typeElements = mutableListOf<TypeElement>()
        var currentTypeElement = this
        val topLevelTypes = listOf(Object::class.java.canonicalName, Any::class.qualifiedName.toString())
        while (!topLevelTypes.contains(currentTypeElement.qualifiedName.toString())) {

            typeElements.add(currentTypeElement)
            currentTypeElement = currentTypeElement.superclass.asTypeElement()
        }
        return typeElements
    }

    fun TypeElement.getFieldsOnlyForHierarchy(adaptInterfaceGetters: Boolean = false): List<VariableElement>{
        return if (adaptInterfaceGetters && ElementKind.INTERFACE == kind) {
            val allMembers = this.getAllMembersForHierarchy()
            allMembers.mapNotNull { elem ->
                if (elem is ExecutableElement && elem.isGetter()) GetterAsFieldAdapter(
                    elem,
                    false,
                    allMembers
                ) else null
            }
        }
        else getAllMembersForHierarchy(true).map { it as VariableElement }
    }

    fun TypeElement.getSettersForHierarchy(): List<ExecutableElement>{
        return getAllMembersForHierarchy(false)
            .mapNotNull {
                if(it is ExecutableElement && it.isSetter()) it else null
            }
    }

    fun ExecutableElement.isCompatibleSetterFor(variableElement: VariableElement): Boolean{
        return isSetter()
                && parameters.single().asType().asTypeElement() ==
                    variableElement.asType().asTypeElement()
                && variableElement.simpleName.toString() ==
                    simpleName.toString().removePrefix("set").decapitalize()
    }

    fun TypeElement.getAllMembersForHierarchy(fieldsOnly: Boolean = false): List<Element> {
        // From the top superclass down to this instance
        return this.getTypeElementHierarchy()
            .reversed()
            // Create a map of members by name
            .map { currentTypeElement ->
                processingEnvironment.elementUtils
                    .getAllMembers(currentTypeElement)
                    .let {
                        if (fieldsOnly) ElementFilter.fieldsIn(it).fieldsOnly()
                        else it
                    }
                    .associateBy { it.simpleName.toString() }
                    .also {
                        processingEnvironment.noteMessage {
                            "getAllMembersForHierarchy, type: ${currentTypeElement.simpleName}, members: ${it.keys.joinToString(",")}"
                        }
                    }
            }
            // Overwrite as we go deeper into the class hierarchy
            .fold(mutableMapOf<String, Element>()) { acc, v ->
                v.forEach { (key, value) ->
                    acc[key] = value
                }
                acc
            }
            .also {
                processingEnvironment.noteMessage {
                    "getAllMembersForHierarchy, all members: ${it.keys.joinToString(",")}"
                }
            }
            .values.toList()
    }

    fun isUpdatable(variableElement: VariableElement): Boolean = !variableElement.hasAnnotation(NoUpdate::class.java)

    fun getFieldInfos(typeElement: TypeElement, outOfAnnotationScope: Boolean = false): List<AnnotatedElementFieldInfo>{
        val allFields =  typeElement.getFieldsOnlyForHierarchy(true)
        val constructorFields =  typeElement.accessibleConstructorParameterFields(true)
        val setters =  typeElement.getSettersForHierarchy()
        return allFields.map {
            SimpleAnnotatedElementFieldInfo(
                variableElement = it,
                isInAnnotationScope = !outOfAnnotationScope,
                isUpdatable = isUpdatable(it),
                isMutableVariable = (!typeElement.isKotlin() && it.modifiers.contains(Modifier.PUBLIC))
                        || setters.find { setter -> setter.isCompatibleSetterFor(it) } != null,
                isConstructorParam = constructorFields.contains(it),
                isConstructorSource = false
            )
        }
    }

    fun getFieldInfos(executableElement: ExecutableElement): List<AnnotatedElementFieldInfo>{
        val containerElement = executableElement.enclosingElement as TypeElement
        val elementFields = getFieldInfos(containerElement, true)
            .map { it as SimpleAnnotatedElementFieldInfo }
        val setters =  containerElement.getSettersForHierarchy()

        val constructorParams = executableElement.parameters
            .associateBy { it.simpleName.toString() }
            .toMutableMap()

        return elementFields.map { field ->
            constructorParams[field.variableElement.simpleName.toString()]
                ?.let { field.copy(isInAnnotationScope = true, isConstructorSource = true) }
                ?: field
        }
    }

    fun TypeElement.isKotlin(): Boolean{
        return processingEnvironment.elementUtils.getAllAnnotationMirrors(this).any { (
                it.annotationType.asElement() as TypeElement).qualifiedName.toString() == "kotlin.Metadata"
        }
    }

    /** Returns all fields in this type that, if a concrete class, also appear as a constructor parameter. */
    fun TypeElement.accessibleConstructorParameterFields(adaptInterfaceGetters: Boolean = false): List<VariableElement> {
        val allMembers = this.getAllMembersForHierarchy()
        val fields = this.getFieldsOnlyForHierarchy()
        val constructorFields = if (fields.isEmpty() && adaptInterfaceGetters && ElementKind.INTERFACE == kind)
            allMembers.mapNotNull { elem ->
                if (elem is ExecutableElement && elem.isGetter()) GetterAsFieldAdapter(
                    elem,
                    false,
                    allMembers
                ) else null
            }
        else {
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

            val nonArgPrefixedConstructorParamNames = constructorParamNames.filterNot { it.startsWith("arg") }
            // Ignore filtering if contructor arg names are missing
            if (nonArgPrefixedConstructorParamNames.isNotEmpty())
                fields.filter { constructorParamNames.contains(it.simpleName.toString()) }
            else fields

        }
        return constructorFields
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

    fun TypeSpec.Builder.copyAnnotationsByBasePackage(
        source: Element,
        basePackages: Iterable<String>
    ): TypeSpec.Builder {
        filterAnnotationsByBasePackage(source, basePackages).forEach {
            this.addAnnotation(AnnotationSpec.get(it))
        }
        return this
    }

    fun PropertySpec.Builder.copyAnnotationsByBasePackage(
        source: Element,
        basePackages: Iterable<String>,
        siteTarget: UseSiteTarget? = null
    ): PropertySpec.Builder {
        filterAnnotationsByBasePackage(source, basePackages).forEach {
            this.addAnnotation(AnnotationSpec.get(it).toBuilder().useSiteTarget(siteTarget).build())
        }
        return this
    }

    fun dtoSpec(dtoInputContext: DtoInputContext): TypeSpec =
        dtoInputContext.dtoStrategy.dtoTypeSpec()

    /**
     * Converts this element to a [TypeName], ensuring that Java types
     * such as [java.lang.String] are converted to their Kotlin equivalent.
     */
    fun Element.asKotlinTypeName(forceMutableCollection: Boolean = false): TypeName = asType().asKotlinTypeName(forceMutableCollection)

    /**
     * Converts this element to a [TypeName], ensuring that Java types
     * such as [java.lang.String] are converted to their Kotlin equivalent.
     */
    fun VariableElement.asKotlinTypeName(forceMutableCollection: Boolean = false): TypeName {
        val typeName = asType().asKotlinTypeName(forceMutableCollection)
        return if (this.isNullable()) typeName.copy(nullable = true) else typeName
    }

    fun TypeName.asKotlinTypeName(forceMutableCollection: Boolean = false): TypeName {
        return if (this is ParameterizedTypeName) {
            val className = rawType.asKotlinTypeName(forceMutableCollection) as ClassName
            className.parameterizedBy(*typeArguments.map { it.asKotlinTypeName(forceMutableCollection) }.toTypedArray())
        } else {
            processingEnvironment.elementUtils.getTypeElement(this.toString())
                .asKotlinClassName(forceMutableCollection)
        }
    }

    /** Converts this TypeMirror to a [TypeName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent. */
    fun TypeMirror.asKotlinTypeName(forceMutableCollection: Boolean = false): TypeName {
        return when (this) {
            is PrimitiveType -> processingEnvironment.typeUtils.boxedClass(this as PrimitiveType?)
                .asKotlinClassName(forceMutableCollection)
            is ArrayType -> {
                return ClassName("kotlin", "Array")
                    .parameterizedBy(this.componentType.asKotlinTypeName(forceMutableCollection))
            }
            is DeclaredType -> {
                val typeName = this.asTypeElement().asKotlinClassName(forceMutableCollection)
                return if (this.typeArguments.isNotEmpty())
                    typeName.parameterizedBy(*typeArguments
                        .mapNotNull { it.asKotlinTypeName(forceMutableCollection) }
                        .toTypedArray())
                else typeName
            }
            else -> this.asTypeName()
        }
    }

    /**
     * Converts this element to a [ClassName], ensuring that java types such as [java.lang.String]
     * are converted to their Kotlin equivalent.
     */
    fun TypeElement.asKotlinClassName(forceMutableCollection: Boolean = false): ClassName {
        val className = asClassName()
        return try {
            // ensure that java.lang.* and java.util.* etc classes are converted to their kotlin equivalents
            var className = Class.forName(className.canonicalName).kotlin.asClassName()
            if(this.isIterable()){
                className = ClassName(className.packageName, "Mutable${className.simpleName}")
            }
            className
        } catch (e: ClassNotFoundException) {
            // probably part of the same source tree as the annotated class
            className
        }
    }

    fun TypeElement.isIterable(): Boolean = this.isAssignableTo(Iterable::class.java, true)

    fun VariableElement.isIterable(): Boolean = this.asType().asTypeElement().isIterable()


    /**
     * Returns true if this element is a subtype of the [targetType], false otherwise.
     * Note that any type is a sub-type of itself.
     * @param erasure whether to use the raw VS the generic [targetType]
     */
    fun TypeElement.isSubTypeOf(targetType: Class<*>, erasure: Boolean = false): Boolean {
        val targetTypeMirror: TypeMirror =
            processingEnvironment.elementUtils.getTypeElement(targetType.canonicalName).asType()
        return processingEnvironment.typeUtils.isSubtype(
            this.asType(),
            if (erasure) processingEnvironment.typeUtils.erasure(targetTypeMirror) else targetTypeMirror
        )
    }

    /**
     * Returns true if this element is assignable to the the [targetType], false otherwise.
     * @param erasure whether to use the raw VS the generic [targetType]
     */
    fun TypeElement.isAssignableTo(targetType: Class<*>, erasure: Boolean = false): Boolean {
        val targetTypeMirror: TypeMirror =
            processingEnvironment.elementUtils.getTypeElement(targetType.canonicalName).asType()
        return processingEnvironment.typeUtils.isAssignable(
            this.asType(),
            if (erasure) processingEnvironment.typeUtils.erasure(targetTypeMirror) else targetTypeMirror
        )
    }

    /** Returns the [TypeElement] represented by this [TypeMirror]. */
    fun TypeMirror.asTypeElement(): TypeElement {
        return if (this is PrimitiveType) {
            val typeName = processingEnvironment.typeUtils.boxedClass(this)
            processingEnvironment.elementUtils.getTypeElement("${typeName}")
        } else processingEnvironment.typeUtils.asElement(this) as TypeElement
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

    /** Check if this type element is an interface */
    fun TypeElement.isInterface(): Boolean {
        return superclass.kind == TypeKind.NONE
                && qualifiedName.toString() != java.lang.Object::class.java.canonicalName
    }

    /** Get the given annotation's value as a [TypeElement] if it exists, throw an error otherwise */
    fun Element.getAnnotationValueAsTypeElement(annotation: Class<out Annotation>, propertyName: String): TypeElement? =
        this.findAnnotationValueAsTypeElement(annotation, propertyName)
            ?: throw IllegalStateException("Could not find a valid value for $propertyName")

    /** Get the given annotation's value as a [TypeElement] if it exists, null otherwise */
    fun Element.findAnnotationValueAsTypeElement(
        annotation: Class<out Annotation>,
        propertyName: String
    ): TypeElement? =
        this.findAnnotationMirror(annotation)?.findValueAsTypeElement(propertyName)

    /** Get the given annotation's value if it exists, null otherwise */
    fun Element.findAnnotationValue(annotation: Class<out Annotation>, propertyName: String): AnnotationValue? =
        this.findAnnotationMirror(annotation)?.findAnnotationValue(propertyName)

    /** Get the given annotation value as a [TypeElement] if it exists, null otherwise */
    fun AnnotationMirror.findValueAsTypeElement(memberName: String): TypeElement? {
        val annotationMirrorValue = this.findValueAsTypeMirror(memberName) ?: return null
        return processingEnvironment.typeUtils.asElement(annotationMirrorValue) as TypeElement?
    }

    /** Get the given annotation value as a [KClass] if it exists and available in the classpath, throw an error otherwise */
    fun AnnotationMirror.getValueAsKClass(memberName: String): KClass<*> {
        return this.findValueAsKClass(memberName)
            ?: throw IllegalStateException("Could not find a valid value for $memberName")
    }

    /** Get the given annotation value as a [KClass] if it exists and available in the classpath, null otherwise */
    fun AnnotationMirror.findValueAsKClass(memberName: String): KClass<*>? {
        val baseFlowAnnotationValue = this.findAnnotationValue(memberName) ?: return null
        return baseFlowAnnotationValue.value as KClass<*>
    }

    /** Get the given annotation value as a [TypeElement] if it exists, null otherwise */
    fun AnnotationMirror.findValueAsTypeMirror(memberName: String): TypeMirror? {
        val baseFlowAnnotationValue = this.findAnnotationValue(memberName) ?: return null
        return baseFlowAnnotationValue.value as TypeMirror
    }

    /** Get the given annotation value as a [TypeElement] if it exists, throw an error otherwise */
    fun AnnotationMirror.getValueAsTypeElement(memberName: String): TypeElement =
        this.findValueAsTypeElement(memberName)
            ?: throw IllegalStateException("Could not find a valid value for $memberName")

    /** Get the given annotation value as a [AnnotationValue] if it exists, throw an error otherwise */
    fun AnnotationMirror.getAnnotationValue(memberName: String): AnnotationValue =
        findAnnotationValue(memberName)
            ?: throw IllegalStateException("Annotation value not found for string '$memberName'")

    /** Get the given annotation value as a [AnnotationValue] if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValue(memberName: String): AnnotationValue? =
        processingEnvironment.elementUtils.getElementValuesWithDefaults(this).entries
            .filter { entry -> entry.key.simpleName.toString() == memberName }
            .mapNotNull { entry -> entry.value }
            .firstOrNull()

    /** Get the given annotation value as a [String] if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValueString(memberName: String): String? =
        findAnnotationValue(memberName)?.value as String?

    /** Get the given annotation value as a [String] if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValueEnum(memberName: String): VariableElement? =
        findAnnotationValue(memberName)?.value as VariableElement?


    /** Get the given annotation value as a list of [AnnotationValue] if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValueList(memberName: String): List<AnnotationValue>? {
        return findAnnotationValue(memberName)?.value as List<AnnotationValue>?
    }

    /** Get the given annotation value as a list of [AnnotationValue] if it exists, null otherwise */
    fun AnnotationMirror.getAnnotationValueList(memberName: String): List<AnnotationValue> {
        return getAnnotationValue(memberName).value as List<AnnotationValue>?
            ?: error("Cound not find a non-null value for annotation member $memberName")
    }

    /** Get the given annotation value as a `List<String>` if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValueListString(memberName: String): List<String>? {
        return findAnnotationValueList(memberName)?.map { it.value.toString() }
    }

    /** Get the given annotation value as a `List<VariableElement>`if it exists, null otherwise */
    fun <T : Enum<T>> AnnotationMirror.findAnnotationValueListEnum(
        memberName: String,
        enumType: Class<T>//TODO: add optiona function ref, use valueOf as default
    ): List<T>? {
        return findAnnotationValueList(memberName)?.map { java.lang.Enum.valueOf(enumType, it.value.toString()) }
    }

    /** Get the given annotation value as a `List<VariableElement>`if it exists, an empty list otherwise */
    fun AnnotationMirror.findAnnotationValueListTypeMirror(memberName: String): List<TypeMirror>? {
        return findAnnotationValueList(memberName)?.map { it.value as TypeMirror }
    }

    /** Get the given annotation value as a `List<TypeElement>`if it exists, null otherwise */
    fun AnnotationMirror.findAnnotationValueListTypeElement(memberName: String): List<TypeElement>? {
        return findAnnotationValueListTypeMirror(memberName)
            ?.map { processingEnvironment.typeUtils.asElement(it) as TypeElement }
    }

    /** Prints an error message using this element as a position hint. */
    fun Element.errorMessage(message: () -> String) {
        processingEnvironment.messager.printMessage(ERROR, message() + "\n", this)
    }

    fun ProcessingEnvironment.errorMessage(message: () -> String) {
        this.messager.printMessage(ERROR, message() + "\n")
    }

    fun ProcessingEnvironment.noteMessage(message: () -> String) {
        this.messager.printMessage(NOTE, message() + "\n")
    }

    fun <T : Any> T.accessField(fieldName: String): Any? {
        return this.javaClass.getDeclaredField(fieldName).let { field ->
            field?.isAccessible = true
            return@let field?.get(this)
        }
    }

    fun CodeBlock.Builder.addStatement(statement: Statement?): CodeBlock.Builder {
        if (statement != null) addStatement(statement.format, *statement.args.toTypedArray())
        return this
    }

    fun String.camelToUnderscores(): String {
        val m: Matcher = camelToUnderscorePattern.matcher(this.decapitalize())

        val sb = StringBuffer()
        while (m.find()) {
            m.appendReplacement(sb, "_" + m.group().toLowerCase())
        }
        m.appendTail(sb)
        return sb.toString()
    }


    fun getStringValuesList(annotationMirror: AnnotationMirror?, memberName: String): List<String> {
        return annotationMirror?.findAnnotationValueListString(memberName) ?: emptyList()
    }

}
