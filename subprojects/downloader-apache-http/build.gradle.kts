plugins {
    id("net.kyori.indra.publishing")
}

description = "An implementation of the resolver Downloader based on the JDK 11 HTTP Client"

dependencies {
    api(project(":vanillagradle-resolver-core"))
    implementation("org.apache.httpcomponents.client5:httpclient5:5.1.3")
}
