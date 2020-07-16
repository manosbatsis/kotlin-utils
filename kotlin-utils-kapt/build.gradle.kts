

apply(plugin = "kotlin-kapt")
/*
project.sourceSets.create(")
java.sourceSets["main"].java {
    srcDir("src/gen/java")
}
sourceSets.getByName("main") {

    java.srcDir("build/generated/source/kaptKotlin/main")
}
*/
dependencies {
    compile(project(":kotlin-utils-api"))
    // Use KotlinPoet
    implementation("com.squareup:kotlinpoet:1.4.1") {
        exclude(group = "org.jetbrains.kotlin")
    }

    api ("com.thinkinglogic.builder:kotlin-builder-annotation:1.2.0") {
        exclude(group = "org.jetbrains.kotlin")
    }
    kapt ("com.thinkinglogic.builder:kotlin-builder-processor:1.2.0") {
        exclude(group = "org.jetbrains.kotlin")
    }
}
