package com.github.manosbatsis.vaultaire.processor.plugins

import com.github.manotbatsis.kotlin.utils.api.AnnotationProcessorPlugin
import java.util.ServiceLoader

class AnnotationProcessorPluginService(
        val classLoader: ClassLoader
) {
    companion object {
        // Cache an instance per classloader
        val serviceLoaders: MutableMap<Int, AnnotationProcessorPluginService> =
                mutableMapOf()

        /** Get a possibly cached instance for the given [ClassLoader] */
        fun forClassLoader(classLoader: ClassLoader) =
                serviceLoaders.getOrPut(
                        System.identityHashCode(classLoader),
                        { AnnotationProcessorPluginService(classLoader) })
    }

    private val loaders: MutableMap<Class<*>, List<*>> =
            mutableMapOf()

    fun <T : AnnotationProcessorPlugin> forServiceType(
            serviceType: Class<T>,
            default: T
    ): T {
        val loaders = loaders.getOrPut(serviceType, {
            ServiceLoader.load(serviceType, classLoader)
                    .map { it }
        }) as List<T>
        return loaders.firstOrNull() ?: default
    }


}