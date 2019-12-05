import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import de.marcphilipp.gradle.nexus.NexusPublishExtension
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel

object Versions {
    const val kotlin = "1.2.71"
    const val kotlinpoet = "1.4.1"
    const val dokka = "0.9.18"

    /* test */
    const val junit = "5.1.1"
    const val jupiter = "5.3.1"
    const val kotlintest = "3.1.10"
}

/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin library project to get you started.
 */

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    id("org.jetbrains.kotlin.jvm") version "1.2.71"
    id("org.jetbrains.dokka") version "0.9.16"
    maven
    `maven-publish`
    signing
    id("de.marcphilipp.nexus-publish") version "0.3.1" apply false
    id("io.codearte.nexus-staging") version "0.21.1"
    idea
}

apply(plugin = "kotlin")
apply<IdeaPlugin>()
apply(plugin = "org.jetbrains.dokka")
apply(plugin = "signing")
apply(plugin = "de.marcphilipp.nexus-publish")

group = "com.github.manosbatsis.kotlinpoet-utils"
version = "0.3-SNAPSHOT"
description ="KotlinPoet Utilities"


repositories {
    mavenLocal()
    jcenter()
    mavenCentral()
}

dependencies {
    // Use the Kotlin JDK 8 standard and reflection libs
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.kotlin}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}")
    // Use KotlinPoet
    implementation("com.squareup:kotlinpoet:${Versions.kotlinpoet}")
    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test:${Versions.kotlin}")
    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

configure<NexusPublishExtension> {
    // We're constantly getting socket timeouts on Travis
    //clientTimeout.set(Duration.ofMinutes(3))

    repositories {
        sonatype()
    }
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        languageVersion = "1.2"
        apiVersion = "1.2"
        freeCompilerArgs = listOf("-Xjsr305=strict")
        javaParameters = true   // Useful for reflection.
    }
}

val sourceSets = the<SourceSetContainer>()
sourceSets {
    getByName("main").java.srcDirs("src/main/kotlin")
    getByName("test").java.srcDirs("src/main/kotlin")
}

val dokkaJar = task<Jar>("dokkaJar") {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    classifier = "javadoc"
    //from(tasks["dokkajar"])
}
val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(sourceSets["main"].java.srcDirs)
}

// based on:
// https://github.com/Ordinastie/MalisisCore/blob/30d8efcfd047ac9e9bc75dfb76642bd5977f0305/build.gradle#L204-L256
// https://github.com/gradle/kotlin-dsl/blob/201534f53d93660c273e09f768557220d33810a9/samples/maven-plugin/build.gradle.kts#L10-L44
val uploadArchives: Upload by tasks
uploadArchives.apply {
    repositories {
        withConvention(MavenRepositoryHandlerConvention::class) {
            mavenDeployer {
                // Sign Maven POM
                beforeDeployment {
                    signing.signPom(this)
                }

                val username =  project.properties["ossrhUsername"] ?: System.getenv("ossrhUsername") ?: System.getenv("USER")
                val password = if (project.hasProperty("ossrhPassword")) project.properties["ossrhPassword"] else System.getenv("ossrhPassword")

                withGroovyBuilder {
                    "snapshotRepository"("url" to "https://oss.sonatype.org/content/repositories/snapshots") {
                        "authentication"("userName" to username, "password" to password)
                    }
                    "repository"("url" to "https://oss.sonatype.org/service/local/staging/deploy/maven2") {
                        "authentication"("userName" to username, "password" to password)
                    }
                }

                // Maven POM generation
                pom.project {
                    withGroovyBuilder {
                        "name"(project.name)
                        "artifactId"(base.archivesBaseName.toLowerCase())
                        "packaging"("jar")
                        "url"("https://github.com/manosbatsis/kotlinpoet-utils")
                        "description"(project.description)
                        "scm" {
                            "connection"("scm:git:git://github.com/manosbatsis/kotlinpoet-utils.git")
                            "developerConnection"("scm:git:ssh://git@github.com:manosbatsis/kotlinpoet-utils.git")
                            "url"("https://github.com/manosbatsis/kotlinpoet-utils")
                        }
                        "licenses" {
                            "license" {
                                "name"("Lesser General Public License, version 3 or greater")
                                "url"("https://github.com/manosbatsis/kotlinpoet-utils/LICENSE")
                                "distribution"("repo")
                            }
                        }
                        "developers" {
                            "developer" {
                                "id"("manosbatsis")
                                "name"("Manos Batsis")
                                "email"("manosbatsis@gmail.com")
                            }
                        }
                        "issueManagement" {
                            "system"("github")
                            "url"("https://github.com/manosbatsis/kotlinpoet-utils/issues")
                        }
                    }
                }
            }
        }
    }
}
// tasks must be before artifacts, don't change the order
artifacts {
    withGroovyBuilder {
        "archives"(tasks["jar"], sourcesJar, dokkaJar)
    }
}
if("<YOUR-PASSWORD>" != project.findProperty("signing.password")) {
    // Conditional signature of artifacts
    signing {
        sign(tasks["jar"], sourcesJar, dokkaJar)
    }

}