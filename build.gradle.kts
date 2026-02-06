import io.whirvex.gradle.isSnapshot
import io.whirvex.gradle.whirvexNexusRelease
import io.whirvex.gradle.whirvexNexusSnapshot

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.dokka") version "2.1.0"
    `java-library`
    `maven-publish`
}

group = "io.whirvex"
version = "2.0.1"

java {
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    jvmToolchain(jdkVersion = 21)
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
    moduleName.set("Whirvex Karwei")
    dokkaSourceSets.main {
        includes.from("src/Module.md")
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }

    repositories {
        if (version.isSnapshot) {
            whirvexNexusSnapshot()
        } else {
            whirvexNexusRelease()
        }
    }
}
