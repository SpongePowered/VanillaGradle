pluginManagement {
    repositories {
        maven("https://repo.spongepowered.org/repository/maven-public/") {
            name = "sponge"
        }
    }

    plugins {
        val indraVersion = "3.0.1"
        id("com.gradle.plugin-publish") version "0.21.0"
        id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.6"
        id("com.diffplug.eclipse.apt") version "3.39.0"
        id("com.diffplug.spotless") version "6.11.0"
        id("net.kyori.indra") version indraVersion
        id("net.kyori.indra.git") version indraVersion
        id("net.kyori.indra.licenser.spotless") version indraVersion
        id("net.kyori.indra.publishing.gradle-plugin") version indraVersion
        id("net.kyori.indra.publishing.sonatype") version indraVersion
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    pluginManagement.repositories.forEach(repositories::add)
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
