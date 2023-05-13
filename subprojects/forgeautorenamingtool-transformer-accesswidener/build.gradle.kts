plugins {
    alias(libs.plugins.indra.publishing)
}

dependencies {
    api(project(":vanillagradle-forgeautorenamingtool-spi"))
    implementation(libs.asm)
    implementation(libs.accessWidener)

    compileOnlyApi(variantOf(libs.immutables.metainf) { classifier("annotations" )})
    annotationProcessor(libs.immutables.metainf)
}
