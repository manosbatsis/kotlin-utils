package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.squareup.kotlinpoet.TypeName

data class AssignmentContext(
        val source: FieldContext,
        val target: FieldContext,
        val fallbackValue: String = "?: error(\"Assignment failed\")",
        val fallbackArgs: List<Any> = emptyList()
) {
    companion object {
        val IN = AssignmentContext(FieldContext.TARGET_TYPE, FieldContext.GENERATED_TYPE)
        val OUT = AssignmentContext(FieldContext.GENERATED_TYPE, FieldContext.TARGET_TYPE)
    }


    fun withFallbackValue(value: String): AssignmentContext =
            copy(fallbackValue = value)

    fun withFallbackArg(arg: TypeName): AssignmentContext =
            copy(fallbackArgs = listOfNotNull(arg))

    fun withFallbackArgs(args: List<Any> = emptyList()): AssignmentContext =
            copy(fallbackArgs = args)
}