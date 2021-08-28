pluginManagement {
    repositories {
        maven("https://repo.spongepowered.org/repository/maven-public/") {
            name = "sponge"
        }
    }

    plugins {
        id("com.gradle.plugin-publish") version "0.15.0"
        id("org.jetbrains.gradle.plugin.idea-ext") version "1.0.1"
        id("com.diffplug.eclipse.apt") version "3.31.0"
        val indraVersion = "2.0.6"
        id("net.kyori.indra") version indraVersion
        id("net.kyori.indra.git") version indraVersion
        id("net.kyori.indra.license-header") version indraVersion
        id("net.kyori.indra.publishing.gradle-plugin") version indraVersion
        id("net.kyori.indra.publishing.sonatype") version indraVersion
    }
}

rootProject.name = "vanillagradle-parent"

// The Gradle plugin maintains a different name convention to preserve history
include("gradle-plugin")
findProject(":gradle-plugin")?.apply {
    name = "vanillagradle"
    projectDir = file("subprojects/gradle-plugin")
}

sequenceOf("resolver-core", "downloader-apache-http", "downloader-jdk-http").forEach {
    include(it)
    findProject(":$it")?.apply {
        name = "vanillagradle-$it"
        projectDir = file("subprojects/$it")
    }
}
