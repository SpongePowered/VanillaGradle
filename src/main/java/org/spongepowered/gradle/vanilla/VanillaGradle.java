/*
 * This file is part of VanillaGradle, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.gradle.vanilla;

import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.spongepowered.gradle.vanilla.task.DownloadAssetsTask;
import org.spongepowered.gradle.vanilla.task.FilterJarTask;
import org.spongepowered.gradle.vanilla.task.MergeJarsTask;
import org.spongepowered.gradle.vanilla.task.RemapJarTask;
import org.spongepowered.gradle.vanilla.task.AccessWidenJarTask;

import java.util.Locale;

public final class VanillaGradle implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getLogger().error(String.format("SpongePowered Vanilla 'GRADLE' Toolset Version '%s'", Constants.VERSION));

        // TODO Make this configurable to not add this always
        project.getRepositories().maven(r -> r.setUrl(Constants.LIBRARIES_MAVEN_URL));

        final MinecraftExtension minecraft = project.getExtensions().create("minecraft", MinecraftExtension.class, project);

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

        final TaskProvider<DownloadAssetsTask> assets = this.createAssetsDownload(minecraft, project.getTasks());

        final TaskProvider<?> prepareWorkspace = project.getTasks().register("prepareWorkspace", DefaultTask.class, task -> {
            task.dependsOn(remapServerJar);
            task.dependsOn(remapClientJar);
            task.dependsOn(mergedJars);
        });

        project.afterEvaluate(p -> {
            minecraft.determineVersion();
            minecraft.downloadManifest();
            minecraft.createMinecraftClasspath(p);

            prepareWorkspace.configure(task -> {
                if (minecraft.platform().get().activeSides().contains(MinecraftSide.CLIENT)) {
                    task.dependsOn(assets);
                }
            });

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
                final TaskProvider<?> actualDependency;
                if (project.getTasks().getNames().contains(Constants.ACCESS_WIDENER_TASK_NAME)) { // Using AW
                    final TaskProvider<AccessWidenJarTask> awTask = project.getTasks().named(
                            Constants.ACCESS_WIDENER_TASK_NAME,
                            AccessWidenJarTask.class
                    );
                    final TaskProvider<?> finalResultJar = resultJar;
                    awTask.configure(task -> {
                        // Configure source
                        task.getSource().from(finalResultJar);
                        // Set suffix based on target platform
                        task.getArchiveClassifier().set("aw-"
                                + minecraft.platform().get().name().toLowerCase(Locale.ROOT)
                                + "-" + minecraft.targetVersion().id());
                    });
                    actualDependency = awTask;
                } else {
                    actualDependency = resultJar;
                }
                project.getDependencies().add(
                        JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                        project.getObjects().fileCollection().from(actualDependency)
                );
            }
        });
    }

    private TaskProvider<RemapJarTask> createSidedTasks(final MinecraftSide platform, final TaskContainer tasks, final MinecraftExtension minecraft) {
        final String sideName = platform.name().toLowerCase(Locale.ROOT);
        final String capitalizedSideName = Character.toUpperCase(sideName.charAt(0)) + sideName.substring(1);

        final TaskProvider<Download> downloadJar = tasks.register("download" + capitalizedSideName, Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().get().activeSides().contains(platform));
            final org.spongepowered.gradle.vanilla.model.Download download = minecraft.targetVersion().download(platform.executableArtifact())
                    .orElseThrow(() -> new RuntimeException("No " + sideName + " download information was within the manifest!"));
            task.src(download.url());
            task.dest(minecraft.minecraftLibrariesDirectory().get().dir(sideName).dir(download.sha1()).file("minecraft-" + sideName + "-" + minecraft
                    .versionDescriptor().id() + ".jar").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<Download> downloadMappings = tasks.register("download" + capitalizedSideName + "Mappings", Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().get().activeSides().contains(platform));
            final org.spongepowered.gradle.vanilla.model.Download download = minecraft.targetVersion().download(platform.mappingsArtifact())
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

    private TaskProvider<DownloadAssetsTask> createAssetsDownload(final MinecraftExtension minecraft, final TaskContainer tasks) {
        // TODO: Attempt to link assets to default client, or other common directories

        // Download asset index
        final TaskProvider<Download> downloadIndex = tasks.register("downloadAssetIndex", Download.class, task -> {
            task.src(minecraft.targetVersion().assetIndex().url());
            task.dest(minecraft.minecraftLibrariesDirectory().dir("assets/indexes").get().getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<DownloadAssetsTask> downloadAssets = tasks.register("downloadAssets", DownloadAssetsTask.class, task -> {
            task.getAssetsDirectory().set(minecraft.minecraftLibrariesDirectory().dir("assets/objects"));
            task.getAssetsIndex().fileProvider(downloadIndex.map(Download::getDest));
        });

        return downloadAssets;
    }
}
