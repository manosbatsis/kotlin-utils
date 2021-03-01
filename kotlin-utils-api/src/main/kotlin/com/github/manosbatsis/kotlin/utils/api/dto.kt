package com.github.manosbatsis.kotlin.utils.api

/** Occurs when a DTO has insufficient information for mapping to a data class */
class DtoInsufficientMappingException(
        message: String = "Error while mapping DTO to data",
        exception: Exception? = null) : RuntimeException("$message: ${exception?.message} ${exception?.cause?.message}", exception)

interface Dto<T : Any> {
    /**
     * Create a patched copy of the given [T] instance,
     * updated using this DTO's non-null properties
     */
    fun toPatched(original: T): T

    /**
     * Create an instance of [T], using this DTO's properties.
     * May throw a [DtoInsufficientMappingException]
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
        val value: String
)