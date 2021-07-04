package com.github.manosbatsis.kotlin.utils.kapt.dto.strategy.composition

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

/**
 * Base strategy composition, only used as a common super-interface
 * by [DtoStrategyComposition] and [DtoMembersStrategy]
 */
interface DtoStrategyLesserComposition : DtoNameStrategy, DtoTypeStrategy {

    val dtoNameStrategy: DtoNameStrategy
    val dtoTypeStrategy: DtoTypeStrategy

    // DtoNameStrategy
    /** Map input - output package name */
    override fun mapPackageName(original: String): String = dtoNameStrategy.mapPackageName(original)

    /** Override to change the DTO package and class name */
    override fun getClassName(): ClassName = dtoNameStrategy.getClassName()

    /** Override to change the DTO package and class name */
    override fun getClassNameSuffix(): String = dtoNameStrategy.getClassNameSuffix()

    // DtoTypeStrategy
    /** Override to change the type-level annotations applied to the DTO  */
    override fun addAnnotations(typeSpecBuilder: TypeSpec.Builder) = dtoTypeStrategy.addAnnotations(typeSpecBuilder)

    /** Override to change the type-level KDoc applied to the DTO  */
    override fun addKdoc(typeSpecBuilder: TypeSpec.Builder) = dtoTypeStrategy.addKdoc(typeSpecBuilder)

    /** Override to change the type-level [KModifier]s applied to the DTO  */
    override fun addModifiers(typeSpecBuilder: TypeSpec.Builder) = dtoTypeStrategy.addModifiers(typeSpecBuilder)

    /** Override to change the super types the DTO extends or implements  */
    override fun addSuperTypes(typeSpecBuilder: TypeSpec.Builder) = dtoTypeStrategy.addSuperTypes(typeSpecBuilder)

    /** Override to change the DTO-specific interface to implement  */
    override fun getDtoInterface(): Class<*> = dtoTypeStrategy.getDtoInterface()

    /** Override to change how the DTO target type is resolved  */
    override fun getDtoTarget(): TypeName = dtoTypeStrategy.getDtoTarget()

}