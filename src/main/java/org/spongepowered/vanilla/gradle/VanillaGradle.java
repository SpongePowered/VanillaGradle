package org.spongepowered.vanilla.gradle;

import de.undercouch.gradle.tasks.download.Download;
import groovy.lang.Closure;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.spongepowered.vanilla.gradle.model.DownloadClassifier;

public final class VanillaGradle implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getLogger().error(String.format("SpongePowered Vanilla 'GRADLE' Toolset Version '%s'", Constants.VERSION));

        final MinecraftExtension minecraft = project.getExtensions().create("minecraft", MinecraftExtension.class);
        minecraft.configure(project);

        project.afterEvaluate(p -> {
            minecraft.determineVersion();
            minecraft.downloadManifest();
            minecraft.createMinecraftClasspath(p);
        });

        project.getTasks().register("downloadServer", Download.class, task -> {
            final org.spongepowered.vanilla.gradle.model.Download download = minecraft.targetVersion().download(DownloadClassifier.SERVER)
                    .orElseThrow(() -> new RuntimeException("No server download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.minecraftLibrariesDirectory().get().dir("server").dir(download.sha1()).file("minecraft-server-" + minecraft
                    .versionDescriptor().id() + ".jar").getAsFile());
            task.overwrite(false);
        });

        project.getTasks().register("downloadClient", Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().get() == MinecraftPlatform.CLIENT);

            final org.spongepowered.vanilla.gradle.model.Download download = minecraft.targetVersion().download(DownloadClassifier.CLIENT)
                    .orElseThrow(() -> new RuntimeException("No client download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.minecraftLibrariesDirectory().get().dir("client").dir(download.sha1()).file("minecraft-client-" + minecraft
                    .versionDescriptor().id() + ".jar").getAsFile());
            task.overwrite(false);
        });
    }
}
