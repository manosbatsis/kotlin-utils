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
    fun getFieldsFromMixin(): List<VariableElement>

    fun List<VariableElement>.excludeNames(excluded: List<String> = emptyList()) =
        filterNot { excluded.contains(it.simpleName.toString()) }

    fun List<VariableElement>.toSimpleNames(excluded: List<String> = emptyList()) =
        excludeNames(excluded).map { it.simpleName.toString() }

    fun getIgnoredFieldNames(): List<String>
}

