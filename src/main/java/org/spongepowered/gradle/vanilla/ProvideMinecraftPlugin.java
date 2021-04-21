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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
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
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.GradleTask;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;
import org.spongepowered.gradle.vanilla.model.Library;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.model.rule.OperatingSystemRule;
import org.spongepowered.gradle.vanilla.model.rule.RuleContext;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftProviderService;
import org.spongepowered.gradle.vanilla.repository.MinecraftRepositoryExtension;
import org.spongepowered.gradle.vanilla.repository.MinecraftRepositoryPlugin;
import org.spongepowered.gradle.vanilla.repository.MinecraftSide;
import org.spongepowered.gradle.vanilla.runs.ClientRunParameterTokens;
import org.spongepowered.gradle.vanilla.task.AccessWidenJarTask;
import org.spongepowered.gradle.vanilla.task.DecompileJarTask;
import org.spongepowered.gradle.vanilla.task.DownloadAssetsTask;
import org.spongepowered.gradle.vanilla.util.IdeConfigurer;
import org.spongepowered.gradle.vanilla.util.StringUtils;

import java.io.File;
import java.util.Iterator;
import java.util.Objects;

/**
 * A plugin that creates the necessary tasks and configurations to provide the
 * Minecraft dependency to a project, but without adding it
 * to any other configurations.
 */
public class ProvideMinecraftPlugin implements Plugin<Project> {

    private Project project;

    @Override
    public void apply(final Project target) {
        target.getPluginManager().apply(MinecraftRepositoryPlugin.class);
        this.project = target;
        final Provider<MinecraftProviderService> minecraftProvider = target.getPlugins().getPlugin(MinecraftRepositoryPlugin.class).service();

        final MinecraftExtensionImpl minecraft = (MinecraftExtensionImpl) target.getExtensions()
            .create(MinecraftExtension.class, "minecraft", MinecraftExtensionImpl.class, target, minecraftProvider);

        final NamedDomainObjectProvider<Configuration> minecraftConfig = target.getConfigurations().register(Constants.Configurations.MINECRAFT, config -> {
            config.setVisible(false);
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);

            config.defaultDependencies(set -> {
                minecraft.platform().disallowChanges();
                minecraft.version().disallowChanges();
                set.add(target.getDependencies().create(minecraft.platform().get().moduleName() + ':' + minecraft.version().get()));
            });

            // TODO: Set appropriate attributes here
        });

        // final TaskProvider<AtlasTransformTask> remapClientJar = this.createSidedTasks(MinecraftSide.CLIENT, target.getTasks(), minecraft);
        // final TaskProvider<AtlasTransformTask> remapServerJar = this.createSidedTasks(MinecraftSide.SERVER, target.getTasks(), minecraft);

        // final TaskProvider<MergeJarsTask> mergedJars = this.createJarMerge(minecraft, remapClientJar, remapServerJar);
        final TaskProvider<DownloadAssetsTask> assets = this.createAssetsDownload(minecraft, minecraftProvider, target.getTasks());

        final TaskProvider<DecompileJarTask> decompileJar = this.createJarDecompile(minecraftConfig, minecraftProvider, minecraft);

        final TaskProvider<?> prepareWorkspace = target.getTasks().register(Constants.Tasks.PREPARE_WORKSPACE, task -> {
            task.setGroup(Constants.TASK_GROUP);
            /*task.dependsOn(remapServerJar);
            task.dependsOn(remapClientJar);
            task.dependsOn(mergedJars);*/
        });

        this.createCleanTasks(target.getTasks(), minecraft);

        target.getPlugins().withType(JavaPlugin.class, $ -> {
            this.createRunTasks(minecraft, target.getTasks(), target.getExtensions().getByType(JavaToolchainService.class));

            // todo: this is not great, we should probably have a separate configuration for run classpath
            minecraft.getRuns().configureEach(run -> run.classpath().from(minecraftConfig));
        });



        target.afterEvaluate(p -> {
            // Only add repositories if selected in extension
            this.configureRepositories(minecraft, p.getRepositories());

            prepareWorkspace.configure(task -> {
                if (minecraft.platform().get().includes(MinecraftSide.CLIENT)) {
                    task.dependsOn(assets);
                }
            });

            this.configureIDEIntegrations(p, minecraft);
        });
    }

    private void configureRepositories(final MinecraftRepositoryExtension extension, final RepositoryHandler handler) {
        if (extension.injectRepositories().get()) {
            Constants.Repositories.applyTo(handler);
        }
    }

    private TaskProvider<DecompileJarTask> createJarDecompile(
        final NamedDomainObjectProvider<Configuration> minecraftConfiguration,
        final Provider<MinecraftProviderService> minecraftProvider,
        final MinecraftExtensionImpl extension
    ) {
        final Configuration forgeFlower = this.project.getConfigurations().maybeCreate(Constants.Configurations.FORGE_FLOWER);
        forgeFlower.defaultDependencies(deps -> deps.add(this.project.getDependencies().create(Constants.WorkerDependencies.FORGE_FLOWER)));
        final FileCollection forgeFlowerClasspath = forgeFlower.getIncoming().getFiles();
        final Provider<ArtifactCollection> minecraftActifacts = minecraftConfiguration.map(mc -> mc.getIncoming().getArtifacts());
        final Provider<MinecraftPlatform> platform = minecraftConfiguration.zip(extension.platform(), (mc, declared) -> {
            final @Nullable Dependency dep = this.extractMinecraftDependency(mc.getAllDependencies());
            if (dep == null) {
                return declared;
            }
            final String requested = dep.getName();
            return MinecraftPlatform.byId(requested)
                .orElseThrow(() -> new InvalidUserDataException("Unknown minecraft platform + " + requested));
        });
        final Provider<String> version = minecraftConfiguration.zip(extension.version(), (mc, declared) -> {
            final @Nullable Dependency dep = this.extractMinecraftDependency(mc.getAllDependencies());
            if (dep == null) {
                return declared;
            }
            return Objects.requireNonNull(dep.getVersion(),"No version provided for MC dependency");
        });

        return this.project.getTasks().register(Constants.Tasks.DECOMPILE, DecompileJarTask.class, task -> {
            task.getMinecraftPlatform().set(platform);
            task.getMinecraftVersion().set(version);
            task.getInputArtifacts().set(minecraftActifacts);
            task.getMinecraftProvider().set(minecraftProvider);
            task.setWorkerClasspath(forgeFlowerClasspath);
        });
    }

    private @Nullable Dependency extractMinecraftDependency(final DependencySet dependencies) {
        // Assuming the `minecraft` configuration contains a 0 or 1 dependencies
        final Iterator<Dependency> it = dependencies.iterator();
        // defaultDependencies is not taken into account here, so we just return null
        if (!it.hasNext()) {
            return null;
        }

        final Dependency dependency = it.next();
        if (it.hasNext()) {
            throw new InvalidUserDataException("Only one dependency should be declared in the 'minecraft' configuration, but multiple were found!");
        }
        return dependency;
    }

    private TaskProvider<DownloadAssetsTask> createAssetsDownload(final MinecraftExtensionImpl minecraft, final Provider<MinecraftProviderService> httpClient, final TaskContainer tasks) {
        // TODO: Update this to use our Downloader and to derive version information from the `minecraft` configuration
        // Download asset index
        final TaskProvider<Download> downloadIndex = this.createDownload(tasks, "downloadAssetIndex",
            minecraft.targetVersion().map(it -> it.assetIndex().url()),
            minecraft.assetsDirectory().dir("indexes")
                .zip(minecraft.targetVersion().map(VersionDescriptor.Full::assetIndex), (indexDir, assetIndex) -> indexDir.file(assetIndex.id() + ".json").getAsFile())
        );

        final NamedDomainObjectProvider<Configuration> natives = this.project.getConfigurations().register(Constants.Configurations.MINECRAFT_NATIVES, config -> {
            config.setVisible(false);
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            config.setTransitive(false);
            config.withDependencies(set -> {
                final RuleContext context = RuleContext.create();
                final String osName = OperatingSystemRule.normalizeOsName(System.getProperty("os.name"));
                for (final Library library : minecraft.targetVersion().get().libraries()) {
                    final String nativeClassifier = library.natives().get(osName); // TODO: Parse this for tokens (ex. natives-windows-${arch})
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
            task.exclude("META-INF/**");
            task.setDuplicatesStrategy(DuplicatesStrategy.WARN); // just in case Mojang change things up on us!
        });

        final TaskProvider<DownloadAssetsTask> downloadAssets = tasks.register(Constants.Tasks.DOWNLOAD_ASSETS, DownloadAssetsTask.class, task -> {
            task.dependsOn(gatherNatives);
            task.getAssetsDirectory().set(minecraft.assetsDirectory().dir("objects"));
            task.getAssetsIndex().fileProvider(downloadIndex.map(Download::getDest));
            task.getHttpClient().set(httpClient);
        });

        minecraft.getRuns().configureEach(run -> {
            run.parameterTokens().put(ClientRunParameterTokens.ASSETS_ROOT, minecraft.assetsDirectory().map(x -> x.getAsFile().getAbsolutePath()));
            run.parameterTokens().put(ClientRunParameterTokens.NATIVES_DIRECTORY, gatherNatives.map(x -> x.getDestinationDir().getAbsolutePath()));
        });

        return downloadAssets;
    }

    private void createCleanTasks(final TaskContainer tasks, final MinecraftExtensionImpl minecraft) {
        // TODO: Update for new ivy repository style
        tasks.register("cleanMinecraft", Delete.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.setDescription("Delete downloaded files for the current minecraft environment used for this project");
            task.delete(
                tasks.withType(AccessWidenJarTask.class),
                tasks.withType(Download.class).matching(dl -> !dl.getName().equals("downloadAssetIndex"))
            );
        });

        tasks.register("cleanAllMinecraft", Delete.class, task -> {
            // As a task that could potentially delete *a lot* of data, let's keep this out of the main task list.
            // task.setGroup(Constants.TASK_GROUP);
            task.setDescription("Delete all cached VanillaGradle data in this project and in shared caches. THIS MAY DELETE A LOT");
            task.delete(
                minecraft.sharedCache(),
                minecraft.projectCache()
            );
        });
    }

    private void configureIDEIntegrations(
        final Project project,
        final MinecraftExtensionImpl extension
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
                            ideaRun.getBeforeRun().create(project.getPath() + JavaPlugin.PROCESS_RESOURCES_TASK_NAME, GradleTask.class,
                                action -> action.setTask(project.getTasks().getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME))
                            );
                        }

                        ideaRun.setMainClass(run.mainClass().get());
                        final File runDirectory = run.workingDirectory().get().getAsFile();
                        ideaRun.setWorkingDirectory(runDirectory.getAbsolutePath());
                        runDirectory.mkdirs();

                        final SourceSet moduleSet;
                        if (run.ideaRunSourceSet().isPresent()) {
                            moduleSet = run.ideaRunSourceSet().get();
                        } else {
                            moduleSet = project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                        }
                        ideaRun.moduleRef(project, moduleSet);
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
                exec.getJavaLauncher().convention(service.launcherFor(this.project.getExtensions().getByType(JavaPluginExtension.class).getToolchain()));
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

    private TaskProvider<Download> createDownload(final TaskContainer tasks, final String name, final Provider<?> source, final Provider<?> destination) {
        return tasks.register(name, Download.class, download -> {
            download.header("User-Agent", Constants.USER_AGENT);
            download.onlyIfModified(true);
            download.quiet(true);
            download.src(source.get());
            download.dest(destination.get());
            download.doFirst(t2 -> ((Download) t2).getDest().getParentFile().mkdirs());
        });
    }

}
