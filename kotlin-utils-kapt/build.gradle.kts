

apply(plugin = "kotlin-kapt")


dependencies {
    compile(project(":kotlin-utils-api"))
    // Use KotlinPoet
    implementation("com.squareup:kotlinpoet:1.4.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.0") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.10") {
        exclude(group = "org.jetbrains.kotlin")
    }
// kotlin metadata
    implementation("me.eugeniomarletti.kotlin.metadata:kotlin-metadata:1.4.0")
    implementation(files("${System.getProperty("java.home")}/../lib/tools.jar"))
    implementation("com.squareup:kotlinpoet:1.4.3")
    implementation("com.google.auto.service:auto-service:1.0-rc7")
    kapt("com.google.auto.service:auto-service:1.0-rc7")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.2.71")

    //api ("com.thinkinglogic.builder:kotlin-builder-annotation:1.2.0") {
    //    exclude(group = "org.jetbrains.kotlin")
    //}
    //kapt ("com.thinkinglogic.builder:kotlin-builder-processor:1.2.0") {
    //    exclude(group = "org.jetbrains.kotlin")
    //}

    //api("com.google.auto.service:auto-service-annotations:1.0-rc7")
    //kapt("com.google.auto.service:auto-service:1.0-rc7")
}
