plugins {
    `java-library`
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.dokka") version "2.1.0-Beta"
}

group = "io.whirvis"
version = "1.0.0-ALPHA"

kotlin {
    jvmToolchain(jdkVersion = 8)
    explicitApi()
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
}

dokka {
    moduleName.set("Karwei")
    dokkaSourceSets.main {
        includes.from("src/Module.md")
    }
}

tasks.test {
    useJUnitPlatform()
}
