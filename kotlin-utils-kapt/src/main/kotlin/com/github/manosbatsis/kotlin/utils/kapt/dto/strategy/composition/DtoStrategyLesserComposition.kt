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
    override fun mapPackageName(original: String): String = dtoNameStrategy.mapPackageName(original)

    override fun getClassName(): ClassName = dtoNameStrategy.getClassName()

    override fun getClassNameSuffix(): String = dtoNameStrategy.getClassNameSuffix()

    // DtoTypeStrategy

    override fun getRootDtoType(): TypeName = dtoTypeStrategy.getRootDtoType()

    override fun addAnnotations(typeSpecBuilder: TypeSpec.Builder) = dtoTypeStrategy.addAnnotations(typeSpecBuilder)

    override fun addKdoc(typeSpecBuilder: TypeSpec.Builder) = dtoTypeStrategy.addKdoc(typeSpecBuilder)

    override fun addModifiers(typeSpecBuilder: TypeSpec.Builder) = dtoTypeStrategy.addModifiers(typeSpecBuilder)

    override fun addSuperTypes(typeSpecBuilder: TypeSpec.Builder) = dtoTypeStrategy.addSuperTypes(typeSpecBuilder)

    override fun getDtoInterface(): Class<*> = dtoTypeStrategy.getDtoInterface()

    override fun getDtoTarget(): TypeName = dtoTypeStrategy.getDtoTarget()

}