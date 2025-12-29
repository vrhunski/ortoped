plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "com.ortoped"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.gradle.org/gradle/libs-releases")  // For Gradle Tooling API
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // ORT Core dependencies - version 74.1.0 (latest as of Dec 2025)
    val ortVersion = "74.1.0"
    implementation("org.ossreviewtoolkit:analyzer:$ortVersion")
    implementation("org.ossreviewtoolkit:model:$ortVersion")

    // ORT Scanner for source code license detection
    implementation("org.ossreviewtoolkit:scanner:$ortVersion")
    implementation("org.ossreviewtoolkit:downloader:$ortVersion")

    // Scanner plugins
    implementation(platform("org.ossreviewtoolkit.plugins:scanners:$ortVersion"))
    implementation("org.ossreviewtoolkit.plugins.scanners:scancode-scanner")

    // VCS plugins (for Git cloning support)
    implementation(platform("org.ossreviewtoolkit.plugins:version-control-systems:$ortVersion"))
    implementation("org.ossreviewtoolkit.plugins.versioncontrolsystems:git-version-control-system")

    // Package manager plugins platform - imports all 23 package managers
    implementation(platform("org.ossreviewtoolkit.plugins:package-managers:$ortVersion"))
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:gradle-package-manager") {
        exclude(group = "org.ossreviewtoolkit.plugins.packagemanagers", module = "gradle-inspector")
    }
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:maven-package-manager")
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:node-package-manager")
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:python-package-manager")
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:cargo-package-manager")

    // HTTP client for Claude API calls (no official Kotlin SDK, using standard HTTP)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // CLI
    implementation("com.github.ajalt.clikt:clikt:4.4.0")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.12")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.ortoped.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}