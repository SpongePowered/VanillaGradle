plugins {
    id("com.gradle.plugin-publish") version "0.12.0"
    `java-gradle-plugin`
    val indraVersion = "1.3.1"
    id("net.kyori.indra") version indraVersion
    id("net.kyori.indra.license-header") version indraVersion
    id("net.kyori.indra.publishing.gradle-plugin") version indraVersion
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

    // Just main
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("de.undercouch:gradle-download-task:4.1.1")
    implementation("org.cadixdev:atlas:0.2.1") {
        exclude("org.ow2.asm") // Use our own ASM
    }
    implementation("org.cadixdev:lorenz:0.5.6")
    implementation("org.cadixdev:lorenz-asm:0.5.6") {
        exclude("org.ow2.asm") // Use our own ASM
    }

    implementation("org.cadixdev:lorenz-io-proguard:0.5.6")

    // IDE support
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:0.10")

    // Jar merge worker (match with Constants)
    "jarMergeCompileOnly"("net.minecraftforge:mergetool:1.1.1") {
        exclude("org.ow2.asm")
    }
    implementation(jarMerge.output)

    // Jar decompile worker (match with Constants)
    "jarDecompileCompileOnly"("net.minecraftforge:forgeflower:1.5.478.18")
    implementation(jarDecompile.output)

    // Access widener worker (match with Constants)
    "accessWidenCompileOnly"("net.fabricmc:access-widener:1.0.2") {
        exclude("org.ow2.asm")
    }
    implementation(accessWiden.output)

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks.jar {
    from(jarMerge.output)
    from(jarDecompile.output)
    from(accessWiden.output)
}

tasks.withType(Jar::class).configureEach {
    manifest.attributes(
            "Git-Commit" to grgit.head().id,
            "Git-Branch" to grgit.branch.current().name,
            "Specification-Title" to "VanillaGradle",
            "Specification-Vendor" to "SpongePowered",
            "Specification-Version" to project.version,
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "SpongePowered"
    )
}

indra {
    github("SpongePowered", "VanillaGradle") {
        ci = true
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
    plugin(
            id = "gradle.vanilla",
            mainClass = "org.spongepowered.gradle.vanilla.VanillaGradle",
            displayName = "VanillaGradle",
            description = "Set up a Minecraft workspace for project development",
            tags = listOf("minecraft", "vanilla")
    )
}

pluginBundle.website = "https://spongepowered.org"

license {
    val name: String by project
    val organization: String by project
    val projectUrl: String by project

    (this as ExtensionAware).extra.apply {
        this["name"] = "VanillaGradle"
        this["organization"] = organization
        this["url"] = projectUrl
    }
    header = project.file("HEADER.txt")
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
