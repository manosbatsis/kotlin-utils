package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy

import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import javax.lang.model.element.VariableElement

interface DtoStrategy  {
    /**
     * Override to change how the [TypeSpec] is generated for this DTO,
     * when overriding other methods is not adequate
     */
    fun dtoTypeSpec(): TypeSpec

    /**
     * Override to change how the [TypeSpec.Builder] is generated for this DTO,
     * when overriding other methods is not adequate
     */
    fun dtoTypeSpecBuilder(): Builder

    /** Process original type fields and add DTO members */
    fun addMembers(typeSpecBuilder: Builder)

    /** Override to modify the fields to process, i.e. replicate for the DTO */
    fun getFieldsToProcess(): List<VariableElement>

    /** Override to modify the mixin fields added to the DTO */
    fun getExtraFieldsFromMixin(): List<VariableElement>

    /** The field names to include */
    fun getFieldIncludes(): List<String> = emptyList()

    /** The field names to exclude */
    fun getFieldExcludes(): List<String> = emptyList()

    fun List<VariableElement>.includeNames(includes: List<String> = emptyList()) =
            if (includes.isEmpty()) this else filter { includes.contains(it.simpleName.toString()) }

    fun List<VariableElement>.excludeNames(excludes: List<String> = emptyList()) =
            if (excludes.isEmpty()) this else filterNot { excludes.contains(it.simpleName.toString()) }

    fun List<VariableElement>.filtered() = includeNames(getFieldIncludes()).excludeNames(getFieldExcludes())

    fun List<VariableElement>.toSimpleNames() = map { it.simpleName.toString() }

}

