plugins {
    alias(libs.plugins.indra.publishing)
}

description = "The core resolver behind VanillaGradle"

dependencies {
    compileOnlyApi(libs.checkerQual)
}
