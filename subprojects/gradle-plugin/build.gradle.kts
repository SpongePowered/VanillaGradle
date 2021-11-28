import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.gradle.ext.TaskTriggersConfig

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
    id("net.kyori.indra.publishing.gradle-plugin")
    id("org.jetbrains.gradle.plugin.idea-ext")
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
}

val accessWidenerVersion: String by project
val asmVersion: String by project
val forgeFlowerVersion: String by project
val forgeAutoRenamingToolVersion: String by project
val junitVersion: String by project
val mergeToolVersion: String by project
dependencies {
    // All source sets
    commonDeps(gradleApi())
    commonDeps("org.ow2.asm:asm:$asmVersion")
    commonDeps("org.ow2.asm:asm-commons:$asmVersion")
    commonDeps("org.ow2.asm:asm-util:$asmVersion")
    commonDeps("net.minecraftforge:ForgeAutoRenamingTool:$forgeAutoRenamingToolVersion") {
        exclude("org.ow2.asm") // Use our own ASM
        exclude("net.sf.jopt-simple")
    }

    // Just main
    implementation("com.google.code.gson:gson:2.8.7")

    compileOnlyApi("org.checkerframework:checker-qual:3.15.0")
    annotationProcessor("org.immutables:value:2.8.8")
    compileOnlyApi("org.immutables:value:2.8.8:annotations")
    api("org.immutables:gson:2.8.8")

    implementation(project(":vanillagradle-resolver-core"))
    implementation(project(":vanillagradle-downloader-apache-http"))

    // IDE support
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.0.1")

    // Jar merge worker (match with Constants)
    "jarMergeCompileOnly"("net.minecraftforge:mergetool:$mergeToolVersion") {
        exclude("org.ow2.asm")
    }
    implementation(jarMerge.output)

    // Jar decompile worker (match with Constants)
    "jarDecompileCompileOnly"("net.minecraftforge:forgeflower:$forgeFlowerVersion")
    implementation(jarDecompile.output)

    // Access widener worker (match with Constants)
    "accessWidenCompileOnly"("net.fabricmc:access-widener:$accessWidenerVersion") {
        exclude("org.ow2.asm")
    }
    implementation(accessWiden.output)

    "shadowCompileOnly"("com.github.jengelman.gradle.plugins:shadow:6.1.0")
    implementation(shadow.output)

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}


tasks {
    // Generate source templates
    val templateSource = project.file("src/main/templates")
    val templateDest = project.layout.buildDirectory.dir("generated/sources/templates")
    val generateTemplates by registering(Copy::class) {
        group = "sponge"
        description = "Generate classes from templates for VanillaGradle"
        val properties = mutableMapOf(
                "asmVersion" to asmVersion,
                "forgeFlowerVersion" to forgeFlowerVersion,
                "mergeToolVersion" to mergeToolVersion,
                "accessWidenerVersion" to accessWidenerVersion
        )
        inputs.properties(properties)

        // Copy template
        from(templateSource)
        into(templateDest)
        expand(properties)
    }

    sourceSets.main {
        java.srcDir(generateTemplates.map { it.outputs })
    }

    // Generate templates on IDE import as well
    (rootProject.idea.project as? ExtensionAware)?.also {
        (it.extensions["settings"] as ExtensionAware).extensions.getByType(TaskTriggersConfig::class).afterSync(generateTemplates)
    }
    project.eclipse {
        synchronizationTasks(generateTemplates)
    }

    val archiveOperations = project.serviceOf<ArchiveOperations>()
    jar {
        from(jarMerge.output)
        from(jarDecompile.output)
        from(accessWiden.output)
        from(shadow.output)
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
