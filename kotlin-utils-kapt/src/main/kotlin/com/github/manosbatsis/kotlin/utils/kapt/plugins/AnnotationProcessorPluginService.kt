package com.github.manosbatsis.kotlin.utils.kapt.plugins

import com.github.manosbatsis.kotlin.utils.kapt.processor.AnnotatedElementInfo
import java.util.*

/** Used by annotation processor to load plugins */
class AnnotationProcessorPluginService private constructor(
        val classLoader: ClassLoader
) {
    companion object {
        // Cache an instance per classloader
        val serviceLoaders: MutableMap<Int, AnnotationProcessorPluginService> =
                mutableMapOf()

        /** Get a possibly cached instance for the given [ClassLoader] */
        fun getInstance(
                classLoader: ClassLoader = AnnotationProcessorPluginService::class.java.classLoader
        ) =
                serviceLoaders.getOrPut(
                        System.identityHashCode(classLoader),
                        { AnnotationProcessorPluginService(classLoader) })
    }

    private val loaders: MutableMap<Class<out AnnotationProcessorPlugin>, List<out AnnotationProcessorPlugin>> =
            mutableMapOf()

    fun <T : AnnotationProcessorPlugin> findPlugins(
            pluginType: Class<T>
    ): Set<T> {
        val candidates = loaders.getOrPut(pluginType, {
            ServiceLoader.load(pluginType, classLoader)
                    .map { it }
        }).map {
            @Suppress("UNCHECKED_CAST")
            it as T
        }
        return candidates.toSet()
    }

    fun <T : AnnotationProcessorPlugin> findPlugin(
            serviceType: Class<T>, annotatedElementInfo: AnnotatedElementInfo, strategy: String? = null
    ): T? {
        val candidates = findPlugins(serviceType).toMutableList()

        candidates.sortBy {
            val priority = it.getSupportPriority(annotatedElementInfo, strategy)
            priority
        }
        val selected = candidates.lastOrNull()
        return selected
    }

    fun <T : AnnotationProcessorPlugin> getPlugin(
            serviceType: Class<T>, annotatedElementInfo: AnnotatedElementInfo, strategy: String? = null
    ): T = findPlugin(serviceType, annotatedElementInfo, strategy)
            ?: error("No matching service for given annotatedElementInfo using type: ${serviceType}, strategy: $strategy")


}
