plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
}

// Version catalog for dependency management - must be defined before subprojects access them
extra["ortVersion"] = "76.0.0"
extra["ktorVersion"] = "2.3.12"
extra["exposedVersion"] = "0.52.0"
extra["coroutinesVersion"] = "1.8.1"
extra["serializationVersion"] = "1.7.1"

allprojects {
    group = "com.ortoped"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.gradle.org/gradle/libs-releases")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
