plugins {
    alias(libs.plugins.indra.publishing)
}

dependencies {
    compileOnlyApi(libs.checkerQual)
    api(libs.forgeAutoRenamingTool)
    api(libs.joptSimple)
    implementation(libs.slf4j)

    compileOnlyApi(variantOf(libs.immutables.metainf) { classifier("annotations" )})
    annotationProcessor(libs.immutables.metainf)
}
