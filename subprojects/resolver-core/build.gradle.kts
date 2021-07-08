plugins {
    id("net.kyori.indra.publishing")
}

description = "The core resolver behind VanillaGradle"

dependencies {
    val checkerVersion: String by project
    compileOnlyApi("org.checkerframework:checker-qual:$checkerVersion")
}