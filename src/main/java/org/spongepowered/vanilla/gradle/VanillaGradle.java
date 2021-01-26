package org.spongepowered.vanilla.gradle;

import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.spongepowered.vanilla.gradle.task.FilterJarTask;
import org.spongepowered.vanilla.gradle.task.MergeJarsTask;
import org.spongepowered.vanilla.gradle.task.RemapJarTask;

import java.util.Locale;

public final class VanillaGradle implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getLogger().error(String.format("SpongePowered Vanilla 'GRADLE' Toolset Version '%s'", Constants.VERSION));

        final MinecraftExtension minecraft = project.getExtensions().create("minecraft", MinecraftExtension.class);
        minecraft.configure(project);

        final TaskProvider<RemapJarTask> remapClientJar = this.createSidedTasks(MinecraftSide.CLIENT, project.getTasks(), minecraft);
        final TaskProvider<RemapJarTask> remapServerJar = this.createSidedTasks(MinecraftSide.SERVER, project.getTasks(), minecraft);

        final TaskProvider<MergeJarsTask> mergedJars = project.getTasks().register("mergeJars", MergeJarsTask.class, task -> {
            task.onlyIf(t -> minecraft.platform().get() == MinecraftPlatform.JOINED);

            task.dependsOn(remapClientJar);
            task.dependsOn(remapServerJar);

            task.getClientJar().set(remapClientJar.get().getOutputJar());
            task.getServerJar().set(remapServerJar.get().getOutputJar());
            task.getMergedJar()
                    .set(minecraft.minecraftLibrariesDirectory().get().dir(Constants.JOINED).dir(minecraft.versionDescriptor().sha1()).file(
                            "minecraft-joined-" + minecraft.versionDescriptor().id() + ".jar"));
        });

        project.getTasks().register("prepareWorkspace", DefaultTask.class, task -> {
            task.dependsOn(remapServerJar);
            task.dependsOn(remapClientJar);
            task.dependsOn(mergedJars);
        });

        project.afterEvaluate(p -> {
            minecraft.determineVersion();
            minecraft.downloadManifest();
            minecraft.createMinecraftClasspath(p);

            TaskProvider<?> resultJar = null;
            switch (minecraft.platform().get()) {
                case CLIENT:
                    resultJar = remapClientJar;
                    break;
                case SERVER:
                    resultJar = remapServerJar;
                    break;
                case JOINED:
                    resultJar = mergedJars;
                    break;
            }

            if (resultJar != null) {
                project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                        project.getObjects().fileCollection().from(resultJar));
            }
        });
    }

    private TaskProvider<RemapJarTask> createSidedTasks(final MinecraftSide platform, final TaskContainer tasks, final MinecraftExtension minecraft) {
        final String sideName = platform.name().toLowerCase(Locale.ROOT);
        final String capitalizedSideName = Character.toUpperCase(sideName.charAt(0)) + sideName.substring(1);

        final TaskProvider<Download> downloadJar = tasks.register("download" + capitalizedSideName, Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().get().activeSides().contains(platform));
            final org.spongepowered.vanilla.gradle.model.Download download = minecraft.targetVersion().download(platform.executableArtifact())
                    .orElseThrow(() -> new RuntimeException("No " + sideName + " download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.minecraftLibrariesDirectory().get().dir(sideName).dir(download.sha1()).file("minecraft-" + sideName + "-" + minecraft
                    .versionDescriptor().id() + ".jar").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<Download> downloadMappings = tasks.register("download" + capitalizedSideName + "Mappings", Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().get().activeSides().contains(platform));
            final org.spongepowered.vanilla.gradle.model.Download download = minecraft.targetVersion().download(platform.mappingsArtifact())
                    .orElseThrow(() -> new RuntimeException("No " + sideName + " mappings download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.mappingsDirectory().get().dir(sideName).dir(download.sha1()).file(sideName + "-" + minecraft
                    .versionDescriptor().id() + ".txt").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<RemapJarTask> remapJar;

        if (platform.allowedPackages().isEmpty()) {
            remapJar = tasks.register("remap" + capitalizedSideName + "Jar", RemapJarTask.class, task -> {
                task.onlyIf(t -> minecraft.platform().get().activeSides().contains(platform));
                task.dependsOn(downloadJar);
                task.dependsOn(downloadMappings);

                task.getInputJar().set(downloadJar.get().getDest());
                task.getOutputJar().set(minecraft.remapDirectory().get().getAsFile().toPath().resolve(sideName).resolve(minecraft.versionDescriptor()
                        .sha1()).resolve("minecraft-" + sideName + "-" + minecraft.versionDescriptor().id() + "-remapped.jar").toFile());
                task.getMappingsFile().set(downloadMappings.get().getDest());
            });
        } else {
            final TaskProvider<FilterJarTask> filteredJar = tasks.register("filter" + capitalizedSideName + "Jar", FilterJarTask.class, task -> {
                task.getAllowedPackages().addAll(platform.allowedPackages());
                task.dependsOn(downloadJar);
                task.fromJar(downloadJar.map(Download::getDest));
                task.getDestinationDirectory().set(minecraft.minecraftLibrariesDirectory().map(path -> path.dir(sideName).dir(minecraft.versionDescriptor().sha1())));
                task.getArchiveClassifier().set("stripped");
                task.getArchiveVersion().set(minecraft.versionDescriptor().id());
                task.getArchiveBaseName().set("minecraft-" + sideName);
            });

            remapJar = tasks.register("remap" + capitalizedSideName + "Jar", RemapJarTask.class, task -> {
                task.onlyIf(t -> minecraft.platform().get().activeSides().contains(platform));
                task.dependsOn(filteredJar);
                task.dependsOn(downloadMappings);

                task.getInputJar().set(filteredJar.flatMap(AbstractArchiveTask::getArchiveFile));
                task.getOutputJar().set(minecraft.remapDirectory().get().getAsFile().toPath().resolve(sideName).resolve(minecraft.versionDescriptor()
                        .sha1()).resolve("minecraft-" + sideName + "-" + minecraft.versionDescriptor().id() + "-remapped.jar").toFile());
                task.getMappingsFile().set(downloadMappings.get().getDest());
            });

        }

        return remapJar;
    }
}
