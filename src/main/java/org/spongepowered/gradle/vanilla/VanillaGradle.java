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

import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
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
import org.spongepowered.gradle.vanilla.task.DisplayMinecraftVersionsTask;
import org.spongepowered.gradle.vanilla.util.StringUtils;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class VanillaGradle implements Plugin<Project> {
    private static final AtomicBoolean VERSION_ANNOUNCED = new AtomicBoolean();

    private Project project;

    @Override
    public void apply(final Project project) {
        this.project = project;

        if (VanillaGradle.VERSION_ANNOUNCED.compareAndSet(false, true)) {
            project.getLogger().lifecycle(String.format("SpongePowered Vanilla 'GRADLE' Toolset Version '%s'", Constants.VERSION));
        }

        project.getPlugins().apply(ProvideMinecraftPlugin.class);
        final MinecraftExtension minecraft = project.getExtensions().getByType(MinecraftExtension.class);
        project.getPlugins().withType(JavaPlugin.class, plugin -> {
            final NamedDomainObjectProvider<Configuration> minecraftConfig = this.project.getConfigurations().named(Constants.Configurations.MINECRAFT);
            Stream.of(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME).forEach(config -> {
                project.getConfigurations().named(config, instance -> instance.extendsFrom(minecraftConfig.get()));
            });

            Stream.of(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME,
                JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME,
                JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).forEach( config -> {
                project.getConfigurations().named(config).configure(path -> path.extendsFrom(minecraft.minecraftClasspathConfiguration()));
            });

            this.createRunTasks(minecraft, project.getTasks(), project.getExtensions().getByType(JavaToolchainService.class));
            final NamedDomainObjectProvider<Configuration> runtimeClasspath = project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
            minecraft.getRuns().configureEach(run -> {
                run.classpath().from(minecraftConfig, minecraft.minecraftClasspathConfiguration(), runtimeClasspath);
            });
        });

        this.createDisplayMinecraftVersions(minecraft, project.getTasks());

        project.afterEvaluate(p -> {
            if (!minecraft.targetVersion().isPresent()) {
                throw new GradleException("No minecraft version has been set! Did you set the version() property in the 'minecraft' extension");
            }
            this.configureIDEIntegrations(project, minecraft, p.getTasks().named(Constants.Tasks.PREPARE_WORKSPACE));
        });
    }

    private void createDisplayMinecraftVersions(final MinecraftExtension extension, final TaskContainer tasks) {
        tasks.register("displayMinecraftVersions", DisplayMinecraftVersionsTask.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.setDescription("Displays all Minecraft versions that can be targeted");
            task.getManifest().set(extension.versionManifest());
        });
    }


    private void configureIDEIntegrations(final Project project, final MinecraftExtension extension, final TaskProvider<?> prepareWorkspaceTask) {
        project.getPlugins().apply(IdeaExtPlugin.class);
        project.getPlugins().apply(EclipsePlugin.class);
        project.getPlugins().withType(IdeaExtPlugin.class, plugin -> this.configureIntellij(project, extension, prepareWorkspaceTask));
        project.getPlugins().withType(EclipsePlugin.class, plugin -> this.configureEclipse(project, plugin, prepareWorkspaceTask));
    }

    private void configureIntellij(final Project project, final MinecraftExtension extension, final TaskProvider<?> prepareWorkspaceTask) {
        final IdeaModel model = project.getExtensions().getByType(IdeaModel.class);
        if (model.getProject() == null) {
            return;
        }

        // Navigate via the extension properties...
        // https://github.com/JetBrains/gradle-idea-ext-plugin/wiki
        final ProjectSettings ideaProjectSettings = ((ExtensionAware) model.getProject()).getExtensions().getByType(ProjectSettings.class);
        final TaskTriggersConfig taskTriggers = ((ExtensionAware) ideaProjectSettings).getExtensions().getByType(TaskTriggersConfig.class);
        final RunConfigurationContainer runConfigurations =
                (RunConfigurationContainer) ((ExtensionAware) ideaProjectSettings).getExtensions().getByName("runConfigurations");

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
                            action -> action.setTask(this.project.getTasks().getByName(Constants.Tasks.DOWNLOAD_ASSETS))
                    );
                    ideaRun.getBeforeRun().register(Constants.Tasks.COLLECT_NATIVES, GradleTask.class,
                            action -> action.setTask(this.project.getTasks().getByName(Constants.Tasks.COLLECT_NATIVES))
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

        // Automatically prepare a workspace after importing
        taskTriggers.afterSync(prepareWorkspaceTask);
    }

    private void configureEclipse(final Project project, final EclipsePlugin eclipse, final TaskProvider<?> prepareWorkspaceTask) {
        final EclipseModel model = project.getExtensions().getByType(EclipseModel.class);

        model.synchronizationTasks(prepareWorkspaceTask);
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
