plugins {
    alias(libs.plugins.indra.publishing)
}

description = "An implementation of the resolver Downloader based on the JDK 11 HTTP Client"

dependencies {
    api(project(":vanillagradle-resolver-core"))
    implementation(libs.apache.httpClient5)
}
