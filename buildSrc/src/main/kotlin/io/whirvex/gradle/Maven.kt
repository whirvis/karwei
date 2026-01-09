/*
 * Copyright Whirvex Software LLC, All rights reserved.
 */
package io.whirvex.gradle

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import java.io.File
import java.net.URI

private val USER_GRADLE_DIR = File(
    System.getProperty("user.home"),
    ".gradle",
)

private val WHIRVEX_GRADLE_CONFIG_DIR = File(
    USER_GRADLE_DIR,
    "whirvex",
)

private val WHIRVEX_NEXUS_CONFIG_FILE = File(
    WHIRVEX_GRADLE_CONFIG_DIR,
    "nexus.config.json",
)

private val WHIRVEX_NEXUS_URI = URI(
    "https://nexus.whirvex.io/"
)

val Any.isSnapshot
    get() = this.toString().endsWith(
        suffix = "snapshot",
        ignoreCase = true,
    )

private fun PasswordCredentials.useWhirvexNexusCredentials() {
    val config = WHIRVEX_NEXUS_CONFIG_FILE.readNexusConfig()
    username = config.credentials.username
    password = config.credentials.password
}

fun RepositoryHandler.whirvexNexusSnapshot():
        MavenArtifactRepository = maven { artifact ->
    artifact.name = "whirvex-maven-snapshot"
    artifact.url = WHIRVEX_NEXUS_URI.resolve("repository/maven-snapshots")
    artifact.credentials { it.useWhirvexNexusCredentials() }
}

fun RepositoryHandler.whirvexNexusRelease():
        MavenArtifactRepository = maven { artifact ->
    artifact.name = "whirvex-maven-releases"
    artifact.url = WHIRVEX_NEXUS_URI.resolve("repository/maven-releases")
    artifact.credentials { it.useWhirvexNexusCredentials() }
}
