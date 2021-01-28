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
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.GradleTask;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;
import org.jetbrains.gradle.ext.TaskTriggersConfig;
import org.spongepowered.gradle.vanilla.model.AssetIndexReference;
import org.spongepowered.gradle.vanilla.model.Version;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.task.DecompileJarTask;
import org.spongepowered.gradle.vanilla.task.DownloadAssetsTask;
import org.spongepowered.gradle.vanilla.task.FilterJarTask;
import org.spongepowered.gradle.vanilla.task.MergeJarsTask;
import org.spongepowered.gradle.vanilla.task.ProcessedJarTask;
import org.spongepowered.gradle.vanilla.task.RemapJarTask;
import org.spongepowered.gradle.vanilla.task.AccessWidenJarTask;
import org.spongepowered.gradle.vanilla.util.StringUtils;

import java.util.Locale;

public final class VanillaGradle implements Plugin<Project> {

    private Project project;

    @Override
    public void apply(final Project project) {
        this.project = project;
        project.getLogger().lifecycle(String.format("SpongePowered Vanilla 'GRADLE' Toolset Version '%s'", Constants.VERSION));

        final MinecraftExtension minecraft = project.getExtensions().create("minecraft", MinecraftExtension.class, project);

        final TaskProvider<RemapJarTask> remapClientJar = this.createSidedTasks(MinecraftSide.CLIENT, project.getTasks(), minecraft);
        final TaskProvider<RemapJarTask> remapServerJar = this.createSidedTasks(MinecraftSide.SERVER, project.getTasks(), minecraft);

        final TaskProvider<MergeJarsTask> mergedJars = this.createJarMerge(minecraft, remapClientJar, remapServerJar);
        final TaskProvider<DownloadAssetsTask> assets = this.createAssetsDownload(minecraft, project.getTasks());

        final TaskProvider<DecompileJarTask> decompileJar = this.createJarDecompile(minecraft);

        final TaskProvider<?> prepareWorkspace = project.getTasks().register("prepareWorkspace", DefaultTask.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.dependsOn(remapServerJar);
            task.dependsOn(remapClientJar);
            task.dependsOn(mergedJars);
        });

        final TaskProvider<?> prepareDecompileWorkspace = project.getTasks().register("prepareDecompileWorkspace", DefaultTask.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.dependsOn(decompileJar);
        });


        project.getPlugins().withType(JavaPlugin.class, plugin -> {
            this.createRunTasks(minecraft, project.getTasks(), project.getExtensions().getByType(JavaToolchainService.class));
            final NamedDomainObjectProvider<Configuration> runtimeClasspath = project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
            minecraft.getRuns().configureEach(run -> {
                run.classpath().from(runtimeClasspath);
            });
        });

        this.createCleanTask(project.getTasks());

        project.afterEvaluate(p -> {
            project.getLogger().lifecycle(String.format("Targeting Minecraft '%s' on a '%s' platform", minecraft.targetVersion().get().id(),
                minecraft.platform().get().name()));

            // Only add repositories if selected in extension
            this.configureRepositories(minecraft, project.getRepositories());
            minecraft.createMinecraftClasspath(p);

            this.configureIDEIntegrations(project, minecraft, prepareWorkspace);
            prepareWorkspace.configure(task -> {
                if (minecraft.platform().get().activeSides().contains(MinecraftSide.CLIENT)) {
                    task.dependsOn(assets);
                }
            });

            TaskProvider<? extends ProcessedJarTask> resultJar = null;
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
                final TaskProvider<? extends ProcessedJarTask> actualDependency;
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
                        task.getArchiveClassifier().set(minecraft.platform().zip(
                            minecraft.targetVersion(),
                            (platform, version ) -> "aw-" + platform.name().toLowerCase(Locale.ROOT) + "-" + version.id()));
                    });
                    actualDependency = awTask;
                    prepareWorkspace.configure(task -> task.dependsOn(actualDependency)); // todo: this is a bit ugly
                } else {
                    actualDependency = resultJar;
                }
                decompileJar.configure(decompile -> {
                    decompile.dependsOn(actualDependency);
                    decompile.getInputJar().set(actualDependency.flatMap(ProcessedJarTask::outputJar));
                });

                project.getDependencies().add(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                    project.getObjects().fileCollection().from(actualDependency)
                );
            }
        });
    }

    private void createCleanTask(final TaskContainer tasks) {
        tasks.register("cleanMinecraft", Delete.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.setDescription("Delete downloaded files for the current minecraft environment used for this project");
            task.delete(
                tasks.withType(AccessWidenJarTask.class),
                tasks.withType(Download.class).matching(dl -> !dl.getName().equals("downloadAssetIndex")),
                tasks.withType(FilterJarTask.class),
                tasks.withType(MergeJarsTask.class),
                tasks.withType(RemapJarTask.class)
            );
        });
    }

    private void configureRepositories(final MinecraftExtension extension, final RepositoryHandler handler) {
        if (extension.injectRepositories().get()) {
            handler.maven(repo -> {
                repo.setUrl(Constants.Repositories.MINECRAFT);
                repo.mavenContent(MavenRepositoryContentDescriptor::releasesOnly);
            });
            handler.maven(repo -> repo.setUrl(Constants.Repositories.FABRIC_MC));
            handler.maven(repo -> repo.setUrl(Constants.Repositories.MINECRAFT_FORGE));
        }
    }

    private TaskProvider<MergeJarsTask> createJarMerge(
            final MinecraftExtension minecraft,
            final TaskProvider<RemapJarTask> client,
            final TaskProvider<RemapJarTask> server
    ) {

        final Configuration mergetool = this.project.getConfigurations().maybeCreate(Constants.Configurations.MERGETOOL);
        mergetool.defaultDependencies(deps -> deps.add(this.project.getDependencies().create(Constants.WorkerDependencies.MERGE_TOOL)));
        final FileCollection mergetoolClasspath = mergetool.getIncoming().getFiles();

        return this.project.getTasks().register("mergeJars", MergeJarsTask.class, task -> {
            final String platformName = minecraft.platform().get().name().toLowerCase(Locale.ROOT);
            task.onlyIf(t -> minecraft.platform().get() == MinecraftPlatform.JOINED);

            task.dependsOn(client);
            task.dependsOn(server);
            task.setWorkerClasspath(mergetoolClasspath);
            task.getClientJar().set(client.get().getOutputJar());
            task.getServerJar().set(server.get().getOutputJar());
            task.getMergedJar().set(
                minecraft.remappedDirectory().zip(minecraft.versionDescriptor(), (dir, version) -> dir.dir(platformName)
                    .dir(version.sha1())
                    .file("minecraft-" + platformName + "-" + version.id() + ".jar"))
            );
        });
    }

    private TaskProvider<DecompileJarTask> createJarDecompile(
        final MinecraftExtension minecraft
    ) {
        final Configuration forgeFlower = this.project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_FLOWER);
        forgeFlower.defaultDependencies(deps -> deps.add(this.project.getDependencies().create(Constants.WorkerDependencies.FORGE_FLOWER)));
        final FileCollection forgeFlowerClasspath = forgeFlower.getIncoming().getFiles();
        final FileCollection minecraftClasspath = minecraft.minecraftClasspathConfiguration().getIncoming().getFiles();

        return this.project.getTasks().register("decompile", DecompileJarTask.class, task -> {
            final String platformName = minecraft.platform().get().name().toLowerCase(Locale.ROOT);
            task.getDecompileClasspath().from(minecraftClasspath);
            task.setWorkerClasspath(forgeFlowerClasspath);
            task.getOutputJar().set(
                minecraft.remappedDirectory().zip(minecraft.versionDescriptor(), (dir, version) -> dir.dir(platformName)
                    .dir(version.sha1())
                    .file("minecraft-" + platformName + "-" + version.id() + "-sources.jar"))
            );
        });
    }

    private TaskProvider<RemapJarTask> createSidedTasks(final MinecraftSide side, final TaskContainer tasks, final MinecraftExtension minecraft) {
        final String sideName = side.name().toLowerCase(Locale.ROOT);
        final String capitalizedSideName = Character.toUpperCase(sideName.charAt(0)) + sideName.substring(1);

        final TaskProvider<Download> downloadJar = tasks.register("download" + capitalizedSideName, Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().get().activeSides().contains(side));
            final Version targetVersion = minecraft.targetVersion().get();
            final org.spongepowered.gradle.vanilla.model.Download download = targetVersion.requireDownload(side.executableArtifact());
            task.src(download.url());
            task.dest(minecraft.originalDirectory().get().dir(sideName).dir(download.sha1())
                    .file("minecraft-" + sideName + "-" + targetVersion.id() + ".jar").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<Download> downloadMappings = tasks.register("download" + capitalizedSideName + "Mappings", Download.class, task -> {
            task.onlyIf(t -> minecraft.platform().get().activeSides().contains(side));
            final Version targetVersion = minecraft.targetVersion().get();
            final org.spongepowered.gradle.vanilla.model.Download download = targetVersion.requireDownload(side.mappingsArtifact());
            task.src(download.url());
            task.dest(minecraft.mappingsDirectory().get().dir(sideName).dir(download.sha1()).file(sideName + "-" + targetVersion.id() + ".txt").getAsFile());
            task.overwrite(false);
        });

        final TaskProvider<RemapJarTask> remapJar;

        if (side.allowedPackages().isEmpty()) {
            remapJar = tasks.register("remap" + capitalizedSideName + "Jar", RemapJarTask.class, task -> {
                task.onlyIf(t -> minecraft.platform().get().activeSides().contains(side));
                task.dependsOn(downloadJar);
                task.dependsOn(downloadMappings);

                task.getInputJar().fileProvider(downloadJar.map(Download::getDest));
                task.getOutputJar().fileProvider(minecraft.remappedDirectory().zip(minecraft.targetVersion(),
                        (remapDir, version) -> {
                            final org.spongepowered.gradle.vanilla.model.Download download = version.requireDownload(side.executableArtifact());
                            return remapDir.getAsFile().toPath().resolve(sideName).resolve(download.sha1())
                                    .resolve("minecraft-" + sideName + "-" + version.id() + "-remapped.jar").toFile();
                        }));
                task.getMappingsFile().fileProvider(downloadMappings.map(Download::getDest));
            });
        } else {
            final TaskProvider<FilterJarTask> filteredJar = tasks.register("filter" + capitalizedSideName + "Jar", FilterJarTask.class, task -> {
                task.onlyIf(t -> minecraft.platform().get().activeSides().contains(side));
                task.getAllowedPackages().addAll(side.allowedPackages());
                task.dependsOn(downloadJar);
                task.fromJar(downloadJar.map(Download::getDest));
                task.getDestinationDirectory().set(minecraft.filteredDirectory().zip(minecraft.targetVersion(),
                        (path, version) -> {
                            final org.spongepowered.gradle.vanilla.model.Download download = version.requireDownload(side.executableArtifact());
                            return path.dir(sideName).dir(download.sha1());
                        }));
                task.getArchiveClassifier().set("filtered");
                task.getArchiveVersion().set(minecraft.versionDescriptor().map(VersionDescriptor::id));
                task.getArchiveBaseName().set("minecraft-" + sideName);
            });

            remapJar = tasks.register("remap" + capitalizedSideName + "Jar", RemapJarTask.class, task -> {
                task.onlyIf(t -> minecraft.platform().get().activeSides().contains(side));
                task.dependsOn(filteredJar);
                task.dependsOn(downloadMappings);

                task.getInputJar().set(filteredJar.flatMap(AbstractArchiveTask::getArchiveFile));
                task.getOutputJar().fileProvider(minecraft.remappedDirectory().zip(minecraft.targetVersion(),
                        (remapDir, version) -> {
                            final org.spongepowered.gradle.vanilla.model.Download download = version.requireDownload(side.executableArtifact());
                            return remapDir.getAsFile().toPath().resolve(sideName).resolve(download.sha1())
                                    .resolve("minecraft-" + sideName + "-" + version.id() + "-remapped.jar").toFile();
                        }));
                task.getMappingsFile().fileProvider(downloadMappings.map(Download::getDest));
            });
        }

        return remapJar;
    }

    private TaskProvider<DownloadAssetsTask> createAssetsDownload(final MinecraftExtension minecraft, final TaskContainer tasks) {
        // TODO: Attempt to link assets to default client, or other common directories

        // Download asset index
        final TaskProvider<Download> downloadIndex = tasks.register("downloadAssetIndex", Download.class, task -> {
            final AssetIndexReference index = minecraft.targetVersion().get().assetIndex();
            task.src(index.url());
            task.dest(minecraft.assetsDirectory().dir("indexes").get().file(index.id() + ".json").getAsFile());
            task.overwrite(false);

            task.doFirst(t2 -> ((Download) t2).getDest().getParentFile().mkdirs());
        });

        final TaskProvider<DownloadAssetsTask> downloadAssets = tasks.register("downloadAssets", DownloadAssetsTask.class, task -> {
            task.getAssetsDirectory().set(minecraft.assetsDirectory().dir("objects"));
            task.getAssetsIndex().fileProvider(downloadIndex.map(Download::getDest));
        });

        return downloadAssets;
    }

    private void configureIDEIntegrations(final Project project, final MinecraftExtension extension, final TaskProvider<?> prepareWorkspaceTask) {
        project.getPlugins().apply(IdeaExtPlugin.class);
        project.getPlugins().apply(EclipsePlugin.class);
        project.getPlugins().withType(IdeaExtPlugin.class, plugin -> this.configureIntellij(project, extension, prepareWorkspaceTask));
        project.getPlugins().withType(EclipsePlugin.class, plugin -> this.configureEclipse(project, plugin, prepareWorkspaceTask));
    }

    private void configureIntellij(final Project project, final MinecraftExtension extension, final TaskProvider<?> prepareWorkspaceTask) {
        final IdeaModel model = project.getExtensions().getByType(IdeaModel.class);

        // Navigate via the extension properties...
        // https://github.com/JetBrains/gradle-idea-ext-plugin/wiki
        final ProjectSettings ideaProjectSettings = ((ExtensionAware) model.getProject()).getExtensions().getByType(ProjectSettings.class);
        final TaskTriggersConfig taskTriggers = ((ExtensionAware) ideaProjectSettings).getExtensions().getByType(TaskTriggersConfig.class);
        final RunConfigurationContainer runConfigurations =
                (RunConfigurationContainer) ((ExtensionAware) ideaProjectSettings).getExtensions().getByName("runConfigurations");

        extension.getRuns().all(run -> {
            // TODO: Make run configuration name configurable
            runConfigurations.create("run" + StringUtils.capitalize(run.getName()) + " (" + project.getName() + ")", Application.class, ideaRun -> {
                if (project.getTasks().getNames().contains(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)) {
                    ideaRun.getBeforeRun().create("processResources", GradleTask.class,
                            action -> action.setTask(project.getTasks().getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME))
                    );
                }

                ideaRun.setMainClass(run.mainClass().get());
                ideaRun.setWorkingDirectory(run.workingDirectory().get().getAsFile().getAbsolutePath());
                // TODO: Figure out if it's possible to set this more appropriately based on the run configuration's classpath
                ideaRun.moduleRef(project, project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME));
                ideaRun.setJvmArgs(StringUtils.join(run.allJvmArguments()));
                ideaRun.setProgramParameters(StringUtils.join(run.allArguments()));
            });
        });

        // Automatically prepare a workspace after importing
        taskTriggers.afterSync(prepareWorkspaceTask);
    }

    private void configureEclipse(final Project project, final EclipsePlugin eclipse, final TaskProvider<?> prepareWorkspaceTask) {
        final EclipseModel model = project.getExtensions().getByType(EclipseModel.class);

        model.synchronizationTasks(prepareWorkspaceTask);
    }

    private void createRunTasks(final MinecraftExtension extension, final TaskContainer tasks, final JavaToolchainService service) {
        extension.getRuns().all(config -> {
            tasks.register("run" + StringUtils.capitalize(config.getName()), JavaExec.class, exec -> {
                exec.setGroup(Constants.TASK_GROUP + " runs");
                exec.getMainClass().set(config.mainClass());
                exec.getMainModule().set(config.mainModule());
                exec.classpath(config.classpath());
                exec.setWorkingDir(config.workingDirectory());
                exec.getJvmArgumentProviders().addAll(config.allJvmArguments());
                exec.getArgumentProviders().addAll(config.allArguments());
            });
        });
    }
}
