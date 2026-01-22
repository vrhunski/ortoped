plugins {
    application
}

dependencies {
    implementation(project(":core"))

    // CLI framework
    implementation("com.github.ajalt.clikt:clikt:4.4.0")

    // Logging (runtime)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.ortoped.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
