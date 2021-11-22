plugins {
    eclipse
    id("org.jetbrains.gradle.plugin.idea-ext")
    id("net.kyori.indra") apply false
    id("net.kyori.indra.git") apply false
    id("net.kyori.indra.license-header") apply false
    id("com.diffplug.eclipse.apt") apply false
}

group = "org.spongepowered"
version = "0.2.1-SNAPSHOT"

subprojects {
    apply(plugin="net.kyori.indra")
    apply(plugin="net.kyori.indra.license-header")
    apply(plugin="net.kyori.indra.git")
    apply(plugin="com.diffplug.eclipse.apt")
    apply(plugin="signing")

    repositories {
        mavenLocal {
          content {
            includeGroup("net.minecraftforge")
          }
        }
        maven("https://repo.spongepowered.org/repository/maven-public/") {
            name = "sponge"
        }
    }

    extensions.configure(net.kyori.indra.IndraExtension::class) {
        github("SpongePowered", "VanillaGradle") {
            ci(true)
        }
        mitLicense()

        javaVersions {
            testWith(8, 11, 16)
        }

        configurePublications {
            pom {
                organization {
                    name.set("SpongePowered")
                    url.set("https://spongepowered.org")
                }
            }
        }

        if (
            project.hasProperty("spongeSnapshotRepo") &&
            project.hasProperty("spongeReleaseRepo")
        ) {
            publishSnapshotsTo("sponge", project.property("spongeSnapshotRepo") as String)
            publishReleasesTo("sponge", project.property("spongeReleaseRepo") as String)
        }
    }

    extensions.configure(org.cadixdev.gradle.licenser.LicenseExtension::class) {
        val organization: String by project
        val projectUrl: String by project

        properties {
            this["name"] = "VanillaGradle"
            this["organization"] = organization
            this["url"] = projectUrl
        }
        header(rootProject.file("HEADER.txt"))
    }

    afterEvaluate {
        extensions.configure(SigningExtension::class) {
            val spongeSigningKey = project.findProperty("spongeSigningKey") as String?
            val spongeSigningPassword = project.findProperty("spongeSigningPassword") as String?
            if (spongeSigningKey != null && spongeSigningPassword != null) {
                val keyFile = rootProject.file(spongeSigningKey)
                if (keyFile.exists()) {
                    useInMemoryPgpKeys(keyFile.readText(Charsets.UTF_8), spongeSigningPassword)
                } else {
                    useInMemoryPgpKeys(spongeSigningKey, spongeSigningPassword)
                }
            } else {
                signatories = PgpSignatoryProvider() // don't use gpg agent
            }
        }
    }

    tasks {
        withType(Jar::class).configureEach {
            // project.extensions.getByType(net.kyori.indra.git.IndraGitExtension::class).applyVcsInformationToManifest(manifest)
            manifest.attributes(
                "Specification-Title" to "VanillaGradle",
                "Specification-Vendor" to "SpongePowered",
                "Specification-Version" to project.version,
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "SpongePowered"
            )
        }

        withType(JavaCompile::class).configureEach {
            options.compilerArgs.add("-Xlint:-processing")
        }
    }
}
