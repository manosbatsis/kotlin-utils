package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.util

import java.beans.Introspector
import javax.lang.model.element.*
import javax.lang.model.type.TypeMirror

/**
 * Used to wrap a getter method (`ExecutableElement`)  as a field (`VariableElement`)
 */
class GetterAsFieldAdapter(
        private val actual: ExecutableElement,
        private val reportOriginalKind: Boolean = false,
        allElements: List<Element> = emptyList()
) : Element by actual, VariableElement {

    companion object {

        fun isGetter(elem: Element): Boolean = with(elem) {
            this is ExecutableElement
                    && ElementKind.METHOD == kind
                    && parameters.isEmpty()
                    && (simpleName.startsWith("get") || simpleName.startsWith("is"))
                    && "$simpleName" != "getClass"
        }

        private fun getterToFieldName(method: ExecutableElement): FieldName {
            val methodName = method.simpleName
            // Assume the method starts with either get or is.
            return FieldName(Introspector.decapitalize(
                    methodName.substring(
                            if (methodName.startsWith("is")) 2 else 3)))
        }

        fun fromGetterOrNull(elem: Element) =
                if (elem is ExecutableElement && isGetter(elem)) GetterAsFieldAdapter(elem)
                else null
    }

    private val fieldName = getterToFieldName(actual)

    private val mutable: Boolean = allElements.any { "$simpleName" == "set${fieldName.toString().capitalize()}" }

    private class FieldName(private val raw: String) : CharSequence by raw, Name {
        override fun contentEquals(other: CharSequence?): Boolean = raw == other
        override fun toString(): String = raw
    }


    override fun asType(): TypeMirror = actual.returnType

    override fun getKind(): ElementKind = if (reportOriginalKind) actual.kind else ElementKind.FIELD

    override fun getSimpleName(): Name = fieldName

    override fun getEnclosedElements(): MutableList<out Element> = mutableListOf()

    override fun getConstantValue(): Any? = null

    override fun toString(): String {
        return "${this.javaClass.simpleName}(actual = ${actual})"
    }

}