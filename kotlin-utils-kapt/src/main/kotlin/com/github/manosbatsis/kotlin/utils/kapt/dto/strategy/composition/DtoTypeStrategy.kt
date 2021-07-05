package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec.Builder

/**
 * Used by [DtoStrategyComposition] implementations to delegate
 * processing related to the generated DTO type.
 */
interface DtoTypeStrategy {
    /** The context object for annotation processing */
    val annotatedElementInfo: AnnotatedElementInfo

    fun getRootDtoType(): TypeName

    /** Override to change the type-level annotations applied to the DTO  */
    fun addAnnotations(typeSpecBuilder: Builder)

    /** Override to change the type-level KDoc applied to the DTO  */
    fun addKdoc(typeSpecBuilder: Builder)

    /** Override to change the type-level [KModifier]s applied to the DTO  */
    fun addModifiers(typeSpecBuilder: Builder)

    /** Override to change the super types the DTO extends or implements  */
    fun addSuperTypes(typeSpecBuilder: Builder)

    /** Override to change the DTO-specific interface to implement  */
    fun getDtoInterface(): Class<*>

    /** Override to change how the DTO target type is resolved  */
    fun getDtoTarget(): TypeName
}



