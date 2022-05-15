package com.github.manosbatsis.kotlin.utils.api


interface Dto<T : Any> {

    companion object {
        protected const val ERR_NULL = "Required property is null: "
        protected const val ERR_NON_UPDATABLE = "Non-updatable property cannot be updated: "
        inline fun errNull(fieldName: String): Nothing =
            throw IllegalArgumentException("${ERR_NULL}$fieldName")

        /**
         * Assumes the field denoted by [fieldName] as non-updatable;
         * throws an error if [newValue] is not null and not equal to [originalValue],
         * returns the [originalValue] otherwise.
         */
        inline fun <T> errNonUpdatableOrOriginalValue(
            fieldName: String,
            newValue: T?,
            originalValue: T
        ): T = if(newValue != null && newValue != originalValue)
            throw IllegalArgumentException("${ERR_NON_UPDATABLE}$fieldName")
        else originalValue
    }

    /** See [Dto.Companion.errNonUpdatableOrOriginalValue] */
    fun <T> errNonUpdatableOrOriginalValue(
        fieldName: String,
        newValue: T?,
        originalValue: T
    ): T = Dto.errNonUpdatableOrOriginalValue(fieldName, newValue, originalValue)

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
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
annotation class DefaultValue(
        val value: String,
        val nullable: Boolean = false
)
