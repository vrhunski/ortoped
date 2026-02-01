plugins {
    `java-library`
}

val ortVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra
val serializationVersion: String by rootProject.extra

dependencies {
    // Kotlin
    api(kotlin("stdlib"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    // ORT Core dependencies
    implementation("org.ossreviewtoolkit:analyzer:$ortVersion")
    implementation("org.ossreviewtoolkit:model:$ortVersion")
    implementation("org.ossreviewtoolkit:scanner:$ortVersion")
    implementation("org.ossreviewtoolkit:downloader:$ortVersion")

    // Scanner plugins
    implementation(platform("org.ossreviewtoolkit.plugins:scanners:$ortVersion"))
    implementation("org.ossreviewtoolkit.plugins.scanners:scancode-scanner")

    // VCS plugins (for Git cloning support)
    implementation(platform("org.ossreviewtoolkit.plugins:version-control-systems:$ortVersion"))
    implementation("org.ossreviewtoolkit.plugins.versioncontrolsystems:git-version-control-system")

    // Package manager plugins
    implementation(platform("org.ossreviewtoolkit.plugins:package-managers:$ortVersion"))
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:gradle-package-manager") {
        exclude(group = "org.ossreviewtoolkit.plugins.packagemanagers", module = "gradle-inspector")
    }
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:maven-package-manager")
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:node-package-manager")
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:python-package-manager")
    implementation("org.ossreviewtoolkit.plugins.packagemanagers:cargo-package-manager")

    // HTTP client for Claude API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // SBOM Generation Libraries
    implementation("org.cyclonedx:cyclonedx-core-java:9.0.4")
    implementation("org.spdx:java-spdx-library:1.1.11")
    implementation("org.spdx:spdx-jackson-store:1.1.9")

    // YAML parsing for policy files
    implementation("com.charleskorn.kaml:kaml:0.56.0")

    // Logging
    api("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.ossreviewtoolkit") {
            useVersion(ortVersion)
        }
    }
}
