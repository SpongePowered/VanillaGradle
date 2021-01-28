plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.cadixdev.licenser") version "0.5.0"
}

group = "org.spongepowered"
version = "0.1"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://files.minecraftforge.net/maven")
    gradlePluginPortal()
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
    // All source sets
    commonDeps(gradleApi())
    commonDeps("org.ow2.asm:asm:$asmVersion")
    commonDeps("org.ow2.asm:asm-commons:$asmVersion")
    commonDeps("org.ow2.asm:asm-util:$asmVersion")

    // Just main
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("de.undercouch:gradle-download-task:4.1.1")
    implementation("org.cadixdev:atlas:0.2.0") {
        exclude("org.ow2.asm") // Use our own ASM
    }
    implementation("org.cadixdev:lorenz:0.5.6")
    implementation("org.cadixdev:lorenz-asm:0.5.4") {
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
    "accessWidenCompileOnly"("net.fabricmc:access-widener:1.0.0") {
        exclude("org.ow2.asm")
    }
    implementation(accessWiden.output)
}

tasks.jar {
    from(jarMerge.output)
    from(jarDecompile.output)
    from(accessWiden.output)
}

gradlePlugin {
    plugins {
        create("vanillagradle") {
            id = "org.spongepowered.gradle.vanilla"
            implementationClass = "org.spongepowered.gradle.vanilla.VanillaGradle"
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

    include("**/*.java")
    newLine = false
}
