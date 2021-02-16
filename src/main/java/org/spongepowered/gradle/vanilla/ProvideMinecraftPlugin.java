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
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.ProjectLibrary;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.GradleTask;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;
import org.spongepowered.gradle.vanilla.model.AssetIndexReference;
import org.spongepowered.gradle.vanilla.model.Library;
import org.spongepowered.gradle.vanilla.model.Version;
import org.spongepowered.gradle.vanilla.model.rule.OperatingSystemRule;
import org.spongepowered.gradle.vanilla.model.rule.RuleContext;
import org.spongepowered.gradle.vanilla.runs.ClientRunParameterTokens;
import org.spongepowered.gradle.vanilla.task.AccessWidenJarTask;
import org.spongepowered.gradle.vanilla.task.AtlasTransformTask;
import org.spongepowered.gradle.vanilla.task.DecompileJarTask;
import org.spongepowered.gradle.vanilla.task.DownloadAssetsTask;
import org.spongepowered.gradle.vanilla.task.MergeJarsTask;
import org.spongepowered.gradle.vanilla.task.ProcessedJarTask;
import org.spongepowered.gradle.vanilla.util.IdeConfigurer;
import org.spongepowered.gradle.vanilla.util.StringUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A plugin that creates the necessary tasks and configurations to provide the
 * Minecraft dependency to a project, but without adding it
 * to any other configurations.
 */
public class ProvideMinecraftPlugin implements Plugin<Project> {

    private Project project;

    @Override
    public void apply(final Project target) {
        this.project = target;

        AtlasTransformTask.registerExecutionCompleteListener(this.project.getGradle());
        final MinecraftExtensionImpl minecraft = (MinecraftExtensionImpl) target.getExtensions()
            .create(MinecraftExtension.class, "minecraft", MinecraftExtensionImpl.class, target);
        final NamedDomainObjectProvider<Configuration> minecraftConfig = target.getConfigurations().register(Constants.Configurations.MINECRAFT, config -> {
            config.setCanBeResolved(true);
            config.setCanBeConsumed(true);
            config.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, target.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, target.getObjects().named(Bundling.class, Bundling.EXTERNAL));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, target.getObjects().named(LibraryElements.class,
                    LibraryElements.JAR));
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8);
            });
            config.extendsFrom(minecraft.minecraftClasspathConfiguration());
        });

        final TaskProvider<AtlasTransformTask> remapClientJar = this.createSidedTasks(MinecraftSide.CLIENT, target.getTasks(), minecraft);
        final TaskProvider<AtlasTransformTask> remapServerJar = this.createSidedTasks(MinecraftSide.SERVER, target.getTasks(), minecraft);

        final TaskProvider<MergeJarsTask> mergedJars = this.createJarMerge(minecraft, remapClientJar, remapServerJar);
        final TaskProvider<DownloadAssetsTask> assets = this.createAssetsDownload(minecraft, target.getTasks());

        final TaskProvider<DecompileJarTask> decompileJar = this.createJarDecompile(minecraft);

        final TaskProvider<?> prepareWorkspace = target.getTasks().register(Constants.Tasks.PREPARE_WORKSPACE, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.dependsOn(remapServerJar);
            task.dependsOn(remapClientJar);
            task.dependsOn(mergedJars);
        });

        this.createCleanMinecraft(target.getTasks());

        target.getPlugins().withType(JavaPlugin.class, $ -> {
            this.createRunTasks(minecraft, target.getTasks(), target.getExtensions().getByType(JavaToolchainService.class));

            minecraft.getRuns().configureEach(run -> run.classpath().from(minecraftConfig, minecraftConfig.map(conf -> conf.getOutgoing().getArtifacts().getFiles())));
        });

        target.afterEvaluate(p -> {
            // Only add repositories if selected in extension
            this.configureRepositories(minecraft, p.getRepositories());
            minecraft.createMinecraftClasspath(p);

            prepareWorkspace.configure(task -> {
                if (minecraft.platform().get().activeSides().contains(MinecraftSide.CLIENT)) {
                    task.dependsOn(assets);
                }
            });

            final TaskProvider<? extends ProcessedJarTask> resultJar;
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
                default:
                    resultJar = null;
                    break;
            }

            if (resultJar != null) {
                final TaskProvider<? extends ProcessedJarTask> actualDependency;
                if (p.getTasks().getNames().contains(Constants.Tasks.ACCESS_WIDENER)) { // Using AW
                    final TaskProvider<AccessWidenJarTask> awTask = p.getTasks().named(
                        Constants.Tasks.ACCESS_WIDENER,
                        AccessWidenJarTask.class
                    );
                    awTask.configure(task -> {
                        // Configure source
                        task.getSource().from(resultJar);
                        // Set suffix based on target platform
                        task.getVersionMetadata().set(minecraft.platform().zip(
                            minecraft.targetVersion(),
                            (pm, v) -> pm.name().toLowerCase(Locale.ROOT) + "-" + v.id()));
                    });
                    actualDependency = awTask;
                    prepareWorkspace.configure(task -> task.dependsOn(actualDependency)); // todo: this is a bit ugly
                } else {
                    actualDependency = resultJar;
                }
                decompileJar.configure(decompile -> {
                    decompile.dependsOn(actualDependency);
                    decompile.getInputJar().set(actualDependency.flatMap(ProcessedJarTask::outputJar));
                    decompile.getOutputJar().fileProvider(actualDependency.flatMap(task -> task.outputJar().map(out -> {
                        final File output = out.getAsFile();
                        final String fileName = output.getName();
                        final int dotIndex = fileName.lastIndexOf('.');
                        final String nameWithoutExtension = dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
                        final String extension = dotIndex == -1 ? "" : fileName.substring(dotIndex);

                        return new File(output.getParentFile(), nameWithoutExtension + "-sources" + extension);
                    })));
                });
                minecraftConfig.configure(mc -> mc.getOutgoing().artifact(actualDependency));

                this.configureIDEIntegrations(p, minecraft, actualDependency, decompileJar);
            }
        });
    }

    private void configureRepositories(final MinecraftExtension extension, final RepositoryHandler handler) {
        if (extension.injectRepositories().get()) {
            handler.maven(repo -> {
                repo.setUrl(Constants.Repositories.MINECRAFT);
                repo.mavenContent(MavenRepositoryContentDescriptor::releasesOnly);
            });
            handler.maven(repo -> repo.setUrl(Constants.Repositories.MINECRAFT_FORGE));
        }
    }

    private TaskProvider<MergeJarsTask> createJarMerge(
        final MinecraftExtensionImpl minecraft,
        final TaskProvider<AtlasTransformTask> client,
        final TaskProvider<AtlasTransformTask> server
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
        final FileCollection minecraftClasspath = this.project.getConfigurations().getByName(Constants.Configurations.MINECRAFT).getIncoming().getFiles();

        return this.project.getTasks().register(Constants.Tasks.DECOMPILE, DecompileJarTask.class, task -> {
            task.getDecompileClasspath().from(minecraftClasspath);
            task.setWorkerClasspath(forgeFlowerClasspath);
        });
    }

    private TaskProvider<AtlasTransformTask> createSidedTasks(final MinecraftSide side, final TaskContainer tasks, final MinecraftExtensionImpl minecraft) {
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

        return tasks.register("remap" + capitalizedSideName + "Jar", AtlasTransformTask.class, task -> {
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

            if (!side.allowedPackages().isEmpty()) {
                task.getTransformations().filterEntries().getAllowedPackages().addAll(side.allowedPackages());
            }

            task.getTransformations().stripSignatures();
            task.getTransformations().remap().getMappingsFile().fileProvider(downloadMappings.map(Download::getDest));
        });
    }

    private TaskProvider<DownloadAssetsTask> createAssetsDownload(final MinecraftExtensionImpl minecraft, final TaskContainer tasks) {
        // TODO: Attempt to link assets to default client, or other common directories

        // Download asset index
        final TaskProvider<Download> downloadIndex = tasks.register("downloadAssetIndex", Download.class, task -> {
            final AssetIndexReference index = minecraft.targetVersion().get().assetIndex();
            task.src(index.url());
            task.dest(minecraft.assetsDirectory().dir("indexes").get().file(index.id() + ".json").getAsFile());
            task.overwrite(false);

            task.doFirst(t2 -> ((Download) t2).getDest().getParentFile().mkdirs());
        });

        final TaskProvider<DownloadAssetsTask> downloadAssets = tasks.register(Constants.Tasks.DOWNLOAD_ASSETS, DownloadAssetsTask.class, task -> {
            task.getAssetsDirectory().set(minecraft.assetsDirectory().dir("objects"));
            task.getAssetsIndex().fileProvider(downloadIndex.map(Download::getDest));
        });

        final NamedDomainObjectProvider<Configuration> natives = this.project.getConfigurations().register("minecraftNatives", config -> {
            config.setVisible(false);
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            config.setTransitive(false);
            config.withDependencies(set -> {
                final RuleContext context = RuleContext.create();
                final String osName = OperatingSystemRule.normalizeOsName(System.getProperty("os.name"));
                for (final Library library : minecraft.targetVersion().get().libraries()) {
                    final String nativeClassifier = library.natives().get(osName);
                    if (nativeClassifier != null && library.rules().test(context) && !library.name().artifact().equals("java-objc-bridge")) {
                        set.add(this.project.getDependencies().create(
                            library.name().group()
                                + ':' + library.name().artifact()
                                + ':' + library.name().version()
                                + ':' + nativeClassifier
                        ));
                    }
                }
            });
        });

        final Provider<FileCollection> nativesFiles = natives.map(config -> config.getIncoming().getFiles());
        final Provider<Directory> nativesDir = this.project.getLayout().getBuildDirectory().dir("run-natives");
        final Provider<FileCollection> extractedNatives = nativesFiles.flatMap(tree -> tree.getElements().map(files -> {
            final ConfigurableFileCollection extracted = this.project.files();
            for (final FileSystemLocation file : files) {
                extracted.from(this.project.zipTree(file));
            }
            return extracted;
        }));
        final TaskProvider<Copy> gatherNatives = tasks.register(Constants.Tasks.COLLECT_NATIVES, Copy.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.from(extractedNatives);
            task.into(nativesDir.get());
        });

        minecraft.getRuns().configureEach(run -> {
            run.parameterTokens().put(ClientRunParameterTokens.ASSETS_ROOT, minecraft.assetsDirectory().map(x -> x.getAsFile().getAbsolutePath()));
            run.parameterTokens().put(ClientRunParameterTokens.NATIVES_DIRECTORY, gatherNatives.map(x -> x.getDestinationDir().getAbsolutePath()));
        });

        return downloadAssets;
    }

    private void createCleanMinecraft(final TaskContainer tasks) {
        tasks.register("cleanMinecraft", Delete.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.setDescription("Delete downloaded files for the current minecraft environment used for this project");
            task.delete(
                tasks.withType(AccessWidenJarTask.class),
                tasks.withType(Download.class).matching(dl -> !dl.getName().equals("downloadAssetIndex")),
                tasks.withType(AtlasTransformTask.class),
                tasks.withType(MergeJarsTask.class)
            );
        });
    }

    private void configureIDEIntegrations(
        final Project project,
        final MinecraftExtensionImpl extension,
        final TaskProvider<? extends ProcessedJarTask> minecraftJarProvider,
        final TaskProvider<DecompileJarTask> decompiledSourcesProvider
    ) {
        IdeConfigurer.apply(project, new IdeConfigurer.IdeImportAction() {
            @Override
            public void idea(final Project project, final IdeaModel idea, final ProjectSettings ideaExtension) {
                final RunConfigurationContainer runConfigurations =
                    (RunConfigurationContainer) ((ExtensionAware) ideaExtension).getExtensions().getByName("runConfigurations");

                extension.getRuns().all(run -> {
                    final String displayName = run.displayName().getOrNull();
                    runConfigurations.create(displayName == null ? run.getName() + " (" + project.getName() + ")" : displayName, Application.class, ideaRun -> {
                        if (project.getTasks().getNames().contains(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)) {
                            ideaRun.getBeforeRun().create(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, GradleTask.class,
                                action -> action.setTask(project.getTasks().getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME))
                            );
                        }

                        if (run.requiresAssetsAndNatives().get()) {
                            ideaRun.getBeforeRun().register(Constants.Tasks.DOWNLOAD_ASSETS, GradleTask.class,
                                action -> action.setTask(project.getTasks().getByName(Constants.Tasks.DOWNLOAD_ASSETS))
                            );
                            ideaRun.getBeforeRun().register(Constants.Tasks.COLLECT_NATIVES, GradleTask.class,
                                action -> action.setTask(project.getTasks().getByName(Constants.Tasks.COLLECT_NATIVES))
                            );
                        }

                        ideaRun.setMainClass(run.mainClass().get());
                        final File runDirectory = run.workingDirectory().get().getAsFile();
                        ideaRun.setWorkingDirectory(runDirectory.getAbsolutePath());
                        runDirectory.mkdirs();

                        // TODO: Figure out if it's possible to set this more appropriately based on the run configuration's classpath
                        ideaRun.moduleRef(project, project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME));
                        ideaRun.setJvmArgs(StringUtils.join(run.allJvmArgumentProviders(), true));
                        ideaRun.setProgramParameters(StringUtils.join(run.allArgumentProviders(), false));
                    });
                });

            }

            @Override
            public void eclipse(final Project project, final EclipseModel eclipse) {
                // TODO: Eclipse run configuration XMLs
            }
        });
    }

    private void createRunTasks(final MinecraftExtension extension, final TaskContainer tasks, final JavaToolchainService service) {
        extension.getRuns().all(config -> {
            tasks.register(config.getName(), JavaExec.class, exec -> {
                exec.setGroup(Constants.TASK_GROUP + " runs");
                if (config.displayName().isPresent()) {
                    exec.setDescription(config.displayName().get());
                }
                exec.setStandardInput(System.in);
                exec.getMainClass().set(config.mainClass());
                exec.getMainModule().set(config.mainModule());
                exec.classpath(config.classpath());
                exec.setWorkingDir(config.workingDirectory());
                exec.getJvmArgumentProviders().addAll(config.allJvmArgumentProviders());
                exec.getArgumentProviders().addAll(config.allArgumentProviders());
                if (config.requiresAssetsAndNatives().get()) {
                    exec.dependsOn(Constants.Tasks.DOWNLOAD_ASSETS);
                    exec.dependsOn(Constants.Tasks.COLLECT_NATIVES);
                }

                exec.doFirst(task -> {
                    final JavaExec je = (JavaExec) task;
                    je.getWorkingDir().mkdirs();
                });
            });
        });
    }

}
