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
}

dependencies {
    val asmVersion: String by project

    implementation("com.google.code.gson:gson:2.8.6")
    implementation("de.undercouch:gradle-download-task:4.1.1")
    implementation("net.minecraftforge:mergetool:1.1.1") {
        exclude("org.ow2.asm") // Use our own ASM
    }
    implementation("org.cadixdev:atlas:0.2.0") {
        exclude("org.ow2.asm") // Use our own ASM
    }
    implementation("org.cadixdev:lorenz:0.5.6")
    implementation("org.cadixdev:lorenz-asm:0.5.4") {
        exclude("org.ow2.asm") // Use our own ASM
    }

    implementation("org.cadixdev:lorenz-io-proguard:0.5.6")
    implementation("net.fabricmc:access-widener:1.0.0") {
        exclude("org.ow2.asm") // Use our own ASM
    }

    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
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
