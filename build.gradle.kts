plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "org.spongepowered"
version = "0.1"

repositories {
    mavenCentral()
    maven {
        setUrl("https://files.minecraftforge.net/maven")
    }
}

gradlePlugin {
    plugins {
        create("vanillagradle") {
            id = "org.spongepowered.vanilla.gradle"
            implementationClass = "org.spongepowered.vanilla.gradle.VanillaGradle"
        }
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
