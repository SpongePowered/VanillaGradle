plugins {
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.indra.publishing.gradlePlugin)
}

dependencies {
    compileOnly(project(":vanillagradle-forgeautorenamingtool-spi"))
    api(libs.mammoth)
}

indraPluginPublishing {
    website("https://spongepowered.org")
    bundleTags(listOf("remapping", "forge"))
    plugin(
        /* id = */ "gradle.remapper",
        /* mainClass = */ "org.spongepowered.gradle.vanilla.remap.RemapPlugin",
        /* displayName = */ "VanillaGradle Remapping",
        /* description = */ "Integrate ForgeAutoRenamingTool (and eventually source remapping) with the Gradle pipeline"
    )
}
