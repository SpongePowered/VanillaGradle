plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.cadixdev.licenser") version "0.5.0"
}

group = "org.spongepowered"
version = "0.1"

repositories {
    mavenCentral()
    maven {
        setUrl("https://files.minecraftforge.net/maven")
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("de.undercouch:gradle-download-task:4.1.1")
    implementation("net.minecraftforge:mergetool:1.1.1")
    implementation("org.cadixdev:atlas:0.2.0")
    implementation("org.cadixdev:lorenz:0.5.6")
    implementation("org.cadixdev:lorenz-asm:0.5.4")
    implementation("org.cadixdev:lorenz-io-proguard:0.5.6")
}

gradlePlugin {
    plugins {
        create("vanillagradle") {
            id = "org.spongepowered.vanilla.gradle"
            implementationClass = "org.spongepowered.vanilla.gradle.VanillaGradle"
        }
    }
}

val name: String by project
val organization: String by project
val projectUrl: String by project

license {
    (this as ExtensionAware).extra.apply {
        this["name"] = name
        this["organization"] = organization
        this["url"] = projectUrl
    }
    header = project.file("HEADER.txt")

    include("**/*.java")
    newLine = false
}
