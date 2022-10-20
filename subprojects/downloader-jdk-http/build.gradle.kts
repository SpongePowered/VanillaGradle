plugins {
    id("net.kyori.indra.publishing")
}

description = "An implementation of the resolver Downloader based on the JDK 11 HTTP Client"

indra {
    javaVersions {
        target(11)
    }
}

dependencies {
    api(project(":vanillagradle-resolver-core"))
}
