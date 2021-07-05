package com.github.manosbatsis.kotlin.utils.api


interface Dto<T : Any> {

    companion object {
        protected const val ERR_NULL = "Required property is null: "
        inline fun errNull(fieldName: String): Nothing =
                throw IllegalArgumentException("${ERR_NULL}$fieldName")
    }


    fun errNull(fieldName: String): Nothing = Dto.errNull(fieldName)

    /**
     * Create a patched copy of the given [T] instance,
     * updated using this DTO's non-null properties
     */
    fun toPatched(original: T): T

    /**
     * Create an instance of [T], using this DTO's properties.
     * May throw an [IllegalStateException]
     * if there is mot enough information to do so.
     */
    fun toTargetType(): T
}

/**
 * Let annotation processors know the desired default value for a property.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
annotation class DefaultValue(
        val value: String,
        val nullable: Boolean = false
)
