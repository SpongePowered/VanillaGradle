plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "org.spongepowered"
version = "0.1"

repositories {
    mavenCentral()
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
}
