package org.spongepowered.vanilla.gradle;

import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.spongepowered.vanilla.gradle.model.DownloadClassifier;
import org.spongepowered.vanilla.gradle.task.MergeJarsTask;
import org.spongepowered.vanilla.gradle.task.RemapJarTask;

public final class VanillaGradle implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getLogger().error(String.format("SpongePowered Vanilla 'GRADLE' Toolset Version '%s'", Constants.VERSION));

        final MinecraftExtension minecraft = project.getExtensions().create("minecraft", MinecraftExtension.class);
        minecraft.configure(project);

        final TaskProvider<Download> downloadServerTask = project.getTasks().register("downloadServer", Download.class, task -> {
            final org.spongepowered.vanilla.gradle.model.Download download = minecraft.targetVersion().download(DownloadClassifier.SERVER)
                    .orElseThrow(() -> new RuntimeException("No server download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.minecraftLibrariesDirectory().get().dir("server").dir(download.sha1()).file("minecraft-server-" + minecraft
                    .versionDescriptor().id() + ".jar").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<Download> downloadClientTask = project.getTasks().register("downloadClient", Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().getOrElse(MinecraftPlatform.SERVER) == MinecraftPlatform.CLIENT);

            final org.spongepowered.vanilla.gradle.model.Download download = minecraft.targetVersion().download(DownloadClassifier.CLIENT)
                    .orElseThrow(() -> new RuntimeException("No client download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.minecraftLibrariesDirectory().get().dir("client").dir(download.sha1()).file("minecraft-client-" + minecraft
                    .versionDescriptor().id() + ".jar").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<Download> downloadServerMappingsTask = project.getTasks().register("downloadServerMappings", Download.class, task -> {
            final org.spongepowered.vanilla.gradle.model.Download download = minecraft.targetVersion().download(DownloadClassifier.SERVER_MAPPINGS)
                    .orElseThrow(() -> new RuntimeException("No server mappings download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.mappingsDirectory().get().dir("server").dir(download.sha1()).file("server-" + minecraft
                    .versionDescriptor().id() + ".txt").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<Download> downloadClientMappingsTask = project.getTasks().register("downloadClientMappings", Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().getOrElse(MinecraftPlatform.SERVER) == MinecraftPlatform.CLIENT);

            final org.spongepowered.vanilla.gradle.model.Download download = minecraft.targetVersion().download(DownloadClassifier.CLIENT_MAPPINGS)
                    .orElseThrow(() -> new RuntimeException("No client mappings download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.mappingsDirectory().get().dir("client").dir(download.sha1()).file("client-" + minecraft
                    .versionDescriptor().id() + ".txt").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<RemapJarTask> remapServerJar = project.getTasks().register("remapServerJar", RemapJarTask.class, task -> {
            task.dependsOn("downloadServer");
            task.dependsOn("downloadServerMappings");

            task.getInputJar().set(downloadServerTask.get().getDest());
            task.getOutputJar().set(minecraft.remapDirectory().get().getAsFile().toPath().resolve("server").resolve(minecraft.versionDescriptor()
                    .sha1()).resolve("minecraft-server-" + minecraft.versionDescriptor().id() + "-remapped.jar").toFile());
            task.getMappingsFile().set(downloadServerMappingsTask.get().getDest());
        });

        final TaskProvider<RemapJarTask> remapClientJar = project.getTasks().register("remapClientJar", RemapJarTask.class, task -> {
            task.onlyIf(t -> minecraft.platform().getOrElse(MinecraftPlatform.SERVER) == MinecraftPlatform.CLIENT);

            task.dependsOn("downloadClient");
            task.dependsOn("downloadClientMappings");

            task.getInputJar().set(downloadClientTask.get().getDest());
            task.getOutputJar().set(minecraft.remapDirectory().get().getAsFile().toPath().resolve("client").resolve(minecraft.versionDescriptor()
                    .sha1()).resolve("minecraft-client-" + minecraft.versionDescriptor().id() + "-remapped.jar").toFile());
            task.getMappingsFile().set(downloadClientMappingsTask.get().getDest());
        });

        final TaskProvider<MergeJarsTask> mergeClientJarsTask = project.getTasks().register("mergeClientJars", MergeJarsTask.class, task -> {
            task.onlyIf(t -> minecraft.platform().getOrElse(MinecraftPlatform.SERVER) == MinecraftPlatform.CLIENT);

            task.dependsOn("remapClientJar");
            task.dependsOn("remapServerJar");

            task.getClientJar().set(remapClientJar.get().getOutputJar());
            task.getServerJar().set(remapServerJar.get().getOutputJar());
            task.getMergedJar()
                    .set(minecraft.minecraftLibrariesDirectory().get().dir(Constants.JOINED).dir(minecraft.versionDescriptor().sha1()).file(
                            "minecraft-joined-" + minecraft.versionDescriptor().id() + ".jar"));
        });

        project.getTasks().register("prepareWorkspace", DefaultTask.class, task -> {
            task.dependsOn("remapServerJar");
            task.dependsOn("mergeClientJars");
        });

        project.afterEvaluate(p -> {
            minecraft.determineVersion();
            minecraft.downloadManifest();
            minecraft.createMinecraftClasspath(p);

            if (minecraft.platform().getOrElse(MinecraftPlatform.CLIENT) == MinecraftPlatform.CLIENT) {
                project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                        project.getObjects().fileCollection().from(mergeClientJarsTask.map(DefaultTask::getOutputs)));
            } else {
                project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                        project.getObjects().fileCollection().from(remapServerJar.map(DefaultTask::getOutputs)));
            }
        });
    }
}
