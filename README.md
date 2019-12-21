# KotlinPoet Utils [![Maven Central](https://img.shields.io/maven-central/v/com.github.manosbatsis.kotlinpoet-utils/kotlinpoet-utils.svg)](http://central.maven.org/maven2/com/github/manosbatsis/kotlinpoet-utils/) 


KotlinPoet/Kapt utilities for Kotlin annotation processor (sub)components.

Add to your build:

```groovy

dependencies {
    // ...
    api("com.github.manosbatsis.kotlinpoet-utils:kotlinpoet-utils:$kotlinpoetutils_version")
}
```

To use, add the `ProcessingEnvironmentAware` to your annotation processor:

```kotlin
import javax.annotation.processing.AbstractProcessor.AbstractProcessor
import com.github.manosbatsis.kotlinpoet.utils.ProcessingEnvironmentAware

class MyAnnotationProcessor : AbstractProcessor(), ProcessingEnvironmentAware {

    
    /**
     * Implement [ProcessingEnvironmentAware.processingEnvironment] 
     * for access to a [ProcessingEnvironment]
     */
    override val processingEnvironment: ProcessingEnvironment by lazy {
        processingEnv
    }
}
```


... or sub-component:

```kotlin
import javax.annotation.processing.AbstractProcessor.AbstractProcessor
import com.github.manosbatsis.kotlinpoet.utils.ProcessingEnvironmentAware

class MyCustomAnnotationProcessingComponent(
    override val processingEnvironment: ProcessingEnvironment 
) : ProcessingEnvironmentAware {
    
    fun doSometing(){
        // Do it!
    }

}
```