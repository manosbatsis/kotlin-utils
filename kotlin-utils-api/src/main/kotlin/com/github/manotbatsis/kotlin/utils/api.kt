package com.github.manosbatsis.kotlin.utils.api

/** Occurs when a DTO has insufficient information for mapping to a data class */
class DtoInsufficientMappingException(
        message: String = "Insufficient information while mapping DTO to data",
        exception: Exception? = null) : RuntimeException(message, exception)

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