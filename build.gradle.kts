plugins {
    id("com.gradle.plugin-publish") version "0.12.0"
    `java-gradle-plugin`
    `maven-publish`
    val indraVersion = "1.2.1"
    id("net.kyori.indra") version indraVersion
    id("net.kyori.indra.license-header") version indraVersion
    id("org.ajoberstar.grgit") version "4.1.0"
}

group = "org.spongepowered"
version = "0.1-SNAPSHOT"

repositories {
    maven("https://repo-new.spongepowered.org/repository/maven-public/") {
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
    javaVersions {
        testWith(8, 11, 15)
    }
}

gradlePlugin {
    plugins {
        create("vanillagradle") {
            id = "org.spongepowered.gradle.vanilla"
            implementationClass = "org.spongepowered.gradle.vanilla.VanillaGradle"
        }
    }
}

pluginBundle {
    website = "https://spongepowered.org"
    vcsUrl = "https://github.com/SpongePowered/VanillaGradle"
    description = "Set up a Minecraft workspace for project development"
    tags = listOf("minecraft", "vanilla")

    plugins {
        named("vanillagradle") {
            displayName = "VanillaGradle"
        }
    }
}

publishing {
    publications {
        withType(MavenPublication::class).configureEach {
            artifactId = project.name.toLowerCase()

            pom {
                name.set(project.name)
                description.set(pluginBundle.description)
                url.set(pluginBundle.website)

                organization {
                    name.set("SpongePowered")
                    url.set("https://spongepowered.org")
                }

                scm {
                    connection.set("scm:git:git://github.com/SpongePowered/VanillaGradle.git")
                    developerConnection.set("scm:git:ssh://github.com/SpongePowered/VanillaGradle.git")
                    url.set(pluginBundle.vcsUrl)
                }

                ciManagement {
                    system.set("GitHub Actions")
                    url.set("https://github.com/SpongePowered/VanillaGradle.git")
                }

                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/SpongePowered/VanillaGradle/raw/master/LICENSE.txt")
                        distribution.set("repo")
                    }
                }
            }
        }
    }

    repositories {
        if (
            project.hasProperty("spongeSnapshotRepo") &&
            project.hasProperty("spongeReleaseRepo")
        ) {
            val repoUrl = if (project.version.toString().endsWith("-SNAPSHOT")) {
                project.property("spongeSnapshotRepo")
            } else {
                project.property("spongeReleaseRepo")
            } as String

            maven(repoUrl) {
                name = "sponge"
                credentials(PasswordCredentials::class)
            }
        }
    }
}

license {
    val name: String by project
    val organization: String by project
    val projectUrl: String by project

    (this as ExtensionAware).extra.apply {
        this["name"] = name
        this["organization"] = organization
        this["url"] = projectUrl
    }
    header = project.file("HEADER.txt")
}
