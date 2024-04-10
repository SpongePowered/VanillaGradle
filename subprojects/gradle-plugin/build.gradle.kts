plugins {
    `java-gradle-plugin`
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.indra.publishing.gradlePlugin)
    alias(libs.plugins.blossom)
    alias(libs.plugins.ideaExt)
}

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
    "jarDecompileCompileClasspath" {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11) // VF needs 11
        }
    }
}

val accessWidenerVersion: String by project
val asmVersion: String by project
val checkerVersion: String by project
val vineFlowerVersion: String by project
val autoRenamingToolVersion: String by project
val junitVersion: String by project
val mergeToolVersion: String by project
dependencies {
    // All source sets
    commonDeps(gradleApi())
    commonDeps(libs.asm)
    commonDeps(libs.asm.commons)
    commonDeps(libs.asm.util)
    commonDeps(libs.autoRenamingTool) {
        exclude("org.ow2.asm") // Use our own ASM
        exclude("net.sf.jopt-simple")
    }
    commonDeps(libs.mammoth)

    // Just main
    implementation(libs.gson)

    compileOnlyApi(libs.checkerQual)
    annotationProcessor(libs.immutables.value)
    compileOnlyApi(variantOf(libs.immutables.value) { classifier("annotations") })
    api(libs.immutables.gson)

    implementation(project(":vanillagradle-resolver-core"))
    implementation(project(":vanillagradle-downloader-apache-http"))

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
