import com.diffplug.gradle.spotless.FormatExtension

plugins {
    eclipse
    id("org.jetbrains.gradle.plugin.idea-ext")
    id("net.kyori.indra") apply false
    id("net.kyori.indra.git") apply false
    id("com.diffplug.spotless")
    id("net.kyori.indra.licenser.spotless") apply false
    id("com.diffplug.eclipse.apt") apply false
}

group = "org.spongepowered"
version = "0.2.1-SNAPSHOT"


spotless {
    format("configs") {
        target("**/*.yaml", "**/*.yml", "**/*.xml", "**/*.json")

        val excludedTargets = mutableListOf(".idea/**", "build/**", ".gradle/**")
        project.subprojects {
            excludedTargets.add(projectDir.toRelativeString(rootDir) + "/**")
        }
        targetExclude(excludedTargets)

        endWithNewline()
        trimTrailingWhitespace()
    }
}

subprojects {
    apply(plugin="net.kyori.indra")
    apply(plugin="net.kyori.indra.licenser.spotless")
    apply(plugin="net.kyori.indra.git")
    apply(plugin="com.diffplug.eclipse.apt")
    apply(plugin="signing")

    extensions.configure(net.kyori.indra.IndraExtension::class) {
        github("SpongePowered", "VanillaGradle") {
            ci(true)
        }
        mitLicense()

        javaVersions {
            testWith(8, 11, 17)
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

    extensions.configure(net.kyori.indra.licenser.spotless.IndraSpotlessLicenserExtension::class) {
        val organization: String by project
        val projectUrl: String by project

        properties().apply {
            put("name", "VanillaGradle")
            put("organization", organization)
            put("url", projectUrl)
        }
        licenseHeaderFile(rootProject.file("HEADER.txt"))
    }

    spotless {
        fun FormatExtension.applyCommonSettings() {
            endWithNewline()
            indentWithSpaces(4)
            trimTrailingWhitespace()
            toggleOffOn("@formatter:off", "@formatter:on")
        }

        java {
            applyCommonSettings()
            importOrderFile(rootProject.file(".spotless/sponge.importorder"))
        }

        kotlinGradle {
            applyCommonSettings()
            // ktlint()
            //    .editorConfigOverride(mapOf("ij_kotlin_imports_layout" to "$*,|,*,|,java.**,|,javax.**"))
        }
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
