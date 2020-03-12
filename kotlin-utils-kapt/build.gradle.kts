dependencies {
    compile(project(":kotlin-utils-api"))
    // Use KotlinPoet
    implementation("com.squareup:kotlinpoet:1.4.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
}
