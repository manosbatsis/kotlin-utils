
KotlinPoet/Kapt utilities for Kotlin annotation processor (sub)components.

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