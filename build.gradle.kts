plugins {
    `java-gradle-plugin`
    eclipse
    id("com.gradle.plugin-publish") version "0.14.0"
    val indraVersion = "2.0.1"
    id("net.kyori.indra") version indraVersion
    id("net.kyori.indra.license-header") version indraVersion
    id("net.kyori.indra.publishing.gradle-plugin") version indraVersion
    id("com.diffplug.eclipse.apt") version "3.29.1"
}

group = "org.spongepowered"
version = "0.2-SNAPSHOT"

repositories {
    maven("https://repo.spongepowered.org/repository/maven-public/") {
        name = "sponge"
    }
}

val commonDeps by configurations.creating
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

configurations.implementation {
    extendsFrom(commonDeps)
}

dependencies {
    val asmVersion: String by project
    val junitVersion: String by project

    // All source sets
    commonDeps(gradleApi())
    commonDeps("org.ow2.asm:asm:$asmVersion")
    commonDeps("org.ow2.asm:asm-commons:$asmVersion")
    commonDeps("org.ow2.asm:asm-util:$asmVersion")
    commonDeps("org.cadixdev:atlas:0.2.1") {
        exclude("org.ow2.asm") // Use our own ASM
    }

    // Just main
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.cadixdev:lorenz:0.5.6")
    implementation("org.cadixdev:lorenz-asm:0.5.6") {
        exclude("org.ow2.asm") // Use our own ASM
    }

    implementation("org.cadixdev:lorenz-io-proguard:0.5.6")

    compileOnlyApi("org.checkerframework:checker-qual:3.12.0")
    annotationProcessor("org.immutables:value:2.8.8")
    compileOnlyApi("org.immutables:value:2.8.8:annotations")
    api("org.immutables:gson:2.8.8")

    implementation("org.apache.httpcomponents.client5:httpclient5:5.0.3")

    // IDE support
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.0")

    // Jar merge worker (match with Constants)
    "jarMergeCompileOnly"("net.minecraftforge:mergetool:1.1.1") {
        exclude("org.ow2.asm")
    }
    implementation(jarMerge.output)

    // Jar decompile worker (match with Constants)
    "jarDecompileCompileOnly"("net.minecraftforge:forgeflower:1.5.498.6")
    implementation(jarDecompile.output)

    // Access widener worker (match with Constants)
    "accessWidenCompileOnly"("net.fabricmc:access-widener:1.0.2") {
        exclude("org.ow2.asm")
    }
    implementation(accessWiden.output)

    "shadowCompileOnly"("com.github.jengelman.gradle.plugins:shadow:6.1.0")
    implementation(shadow.output)

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks.jar {
    from(jarMerge.output)
    from(jarDecompile.output)
    from(accessWiden.output)
    from(shadow.output)
}

tasks.withType(Jar::class).configureEach {
    indraGit.applyVcsInformationToManifest(manifest)
    manifest.attributes(
            "Specification-Title" to "VanillaGradle",
            "Specification-Vendor" to "SpongePowered",
            "Specification-Version" to project.version,
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "SpongePowered"
    )
}

tasks.withType(JavaCompile::class).configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}

indra {
    github("SpongePowered", "VanillaGradle") {
        ci(true)
    }
    mitLicense()

    javaVersions {
        testWith(8, 11, 15)
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

license {
    val organization: String by project
    val projectUrl: String by project

    properties {
        this["name"] = "VanillaGradle"
        this["organization"] = organization
        this["url"] = projectUrl
    }
    header("HEADER.txt")
}

signing {
    val spongeSigningKey = project.findProperty("spongeSigningKey") as String?
    val spongeSigningPassword = project.findProperty("spongeSigningPassword") as String?
    if (spongeSigningKey != null && spongeSigningPassword != null) {
        val keyFile = file(spongeSigningKey)
        if (keyFile.exists()) {
            useInMemoryPgpKeys(file(spongeSigningKey).readText(Charsets.UTF_8), spongeSigningPassword)
        } else {
            useInMemoryPgpKeys(spongeSigningKey, spongeSigningPassword)
        }
    } else {
        signatories = PgpSignatoryProvider() // don't use gpg agent
    }
}
