dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.9.0") {
        exclude(group = "org.jetbrains.kotlin")
    }
}