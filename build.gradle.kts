import com.diffplug.gradle.spotless.FormatExtension

plugins {
    eclipse
    alias(libs.plugins.ideaExt)
    alias(libs.plugins.indra) apply false
    alias(libs.plugins.indra.publishing) apply false
    alias(libs.plugins.indra.git) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.indra.licenserSpotless) apply false
    alias(libs.plugins.eclipseApt) apply false
}

group = "org.spongepowered"
version = "0.2.2-SNAPSHOT"


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
            target(11)
            testWith(11, 17, 21)
        }

        configurePublications {
            pom {
                organization {
                    name.set("SpongePowered")
                    url.set("https://spongepowered.org")
                }
            }
        }

        signWithKeyFromPrefixedProperties("sponge")
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
            targetExclude("build/generated/**")
            applyCommonSettings()
            importOrderFile(rootProject.file(".spotless/sponge.importorder"))
        }

        kotlinGradle {
            applyCommonSettings()
            // ktlint()
            //    .editorConfigOverride(mapOf("ij_kotlin_imports_layout" to "$*,|,*,|,java.**,|,javax.**"))
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

            doFirst {
                if (javaCompiler.get().metadata.languageVersion >= JavaLanguageVersion.of(21)) {
                    options.compilerArgs.add("-Xlint:-this-escape")
                }
            }
        }
    }
}
