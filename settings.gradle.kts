pluginManagement {
    repositories {
        maven("https://repo.spongepowered.org/repository/maven-public/") {
            name = "sponge"
        }
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

sequenceOf(
        "resolver-core",
        "downloader-apache-http",
        "downloader-jdk-http",
        "forgeautorenamingtool-spi",
        "forgeautorenamingtool-transformers",
        "forgeautorenamingtool-transformer-accesswidener",
        "plugin-remapper"
).forEach {
    include(it)
    findProject(":$it")?.apply {
        name = "vanillagradle-$it"
        projectDir = file("subprojects/$it")
    }
}
