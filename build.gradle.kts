import com.diffplug.gradle.spotless.FormatExtension

plugins {
    `java-gradle-plugin`
    eclipse
    signing
    alias(libs.plugins.ideaExt)
    alias(libs.plugins.indra)
    alias(libs.plugins.indra.git)
    alias(libs.plugins.spotless)
    alias(libs.plugins.indra.licenserSpotless)
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.indra.publishing.gradlePlugin)
    alias(libs.plugins.blossom)
    alias(libs.plugins.eclipseApt)
}

group = "org.spongepowered"
version = "0.3.1-SNAPSHOT"

val commonDeps by configurations.creating {
}
val jarMerge by sourceSets.creating {
    configurations.named(this.implementationConfigurationName) { extendsFrom(commonDeps) }
}
val jarDecompile by sourceSets.creating {
    configurations.named(this.implementationConfigurationName) { extendsFrom(commonDeps) }
}
val accessWiden by sourceSets.creating {
    configurations.named(this.implementationConfigurationName) { extendsFrom(commonDeps) }
}
val shadow by sourceSets.creating {
    configurations.named(this.implementationConfigurationName) { extendsFrom(commonDeps) }
}

configurations {
    api { extendsFrom(commonDeps) }
}

val accessWidenerVersion: String by project
val asmVersion: String by project
val checkerVersion: String by project
val vineFlowerVersion: String by project
val junitVersion: String by project
val mergeToolVersion: String by project
dependencies {
    // All source sets
    commonDeps(gradleApi())
    commonDeps(libs.asm)
    commonDeps(libs.asm.commons)
    commonDeps(libs.asm.util)
    commonDeps(libs.mammoth)

    // Just main
    implementation(libs.gson)

    compileOnlyApi(libs.checkerQual)
    annotationProcessor(libs.immutables.value)
    compileOnlyApi(variantOf(libs.immutables.value) { classifier("annotations") })
    api(libs.immutables.gson)

    // IDE support
    implementation(libs.ideaExt)

    // Jar merge worker (match with Constants)
    "jarMergeCompileOnly"(libs.mergeTool) {
        exclude("org.ow2.asm")
    }
    implementation(jarMerge.output)

    // Jar decompile worker (match with Constants)
    "jarDecompileCompileOnly"(libs.vineFlower)
    implementation(jarDecompile.output)

    // Access widener worker (match with Constants)
    "accessWidenCompileOnly"(libs.accessWidener) {
        exclude("org.ow2.asm")
    }
    implementation(accessWiden.output)

    "shadowCompileOnly"(libs.shadowPlugin)
    implementation(shadow.output)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.junit.engine)
}

sourceSets.main {
    blossom.javaSources {
        properties.putAll(mutableMapOf(
            "asmVersion" to libs.versions.asm.get(),
            "vineFlowerVersion" to libs.versions.vineFlower.get(),
            "mergeToolVersion" to libs.versions.mergeTool.get(),
            "accessWidenerVersion" to libs.versions.accessWidener.get()
        ))
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
        options.compilerArgs.addAll(listOf("-Xlint:-processing", "-Xlint:-this-escape"))
    }

    jar {
        from(jarMerge.output)
        from(jarDecompile.output)
        from(accessWiden.output)
        from(shadow.output)
    }

    publishPlugins {
        onlyIf { net.kyori.indra.util.Versioning.isRelease(project) }
    }
}

indraPluginPublishing {
    website("https://spongepowered.org")
    bundleTags(listOf("minecraft", "vanilla"))
    plugin(
        /* id = */ "gradle.vanilla",
        /* mainClass = */ "org.spongepowered.gradle.vanilla.VanillaGradle",
        /* displayName = */ "VanillaGradle",
        /* description = */ "Set up a Minecraft workspace for project development"
    )
}

extensions.configure(net.kyori.indra.IndraExtension::class) {
    github("SpongePowered", "VanillaGradle") {
        ci(true)
    }
    mitLicense()

    javaVersions {
        target(25)
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

    format("configs") {
        target("**/*.yaml", "**/*.yml", "**/*.xml", "**/*.json")
        targetExclude(".idea/**", "build/**", ".gradle/**", "src/test/**")

        endWithNewline()
        trimTrailingWhitespace()
    }
}
