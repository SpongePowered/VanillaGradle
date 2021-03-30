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
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.TaskTriggersConfig;
import org.spongepowered.gradle.vanilla.model.DownloadClassifier;
import org.spongepowered.gradle.vanilla.model.VersionClassifier;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.task.DisplayMinecraftVersionsTask;
import org.spongepowered.gradle.vanilla.util.IdeConfigurer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class VanillaGradle implements Plugin<Project> {
    private static final AtomicBoolean VERSION_ANNOUNCED = new AtomicBoolean();

    @Override
    public void apply(final Project project) {
        if (VanillaGradle.VERSION_ANNOUNCED.compareAndSet(false, true)) {
            project.getLogger().lifecycle(String.format("SpongePowered Vanilla 'GRADLE' Toolset Version '%s'", Constants.VERSION));
        }

        project.getPlugins().apply(ProvideMinecraftPlugin.class);
        final MinecraftExtensionImpl minecraft = (MinecraftExtensionImpl) project.getExtensions().getByType(MinecraftExtension.class);
        project.getPlugins().withType(JavaPlugin.class, plugin -> {
            Stream.of(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME).forEach(config -> {
                project.getDependencies().add(config, minecraft.minecraftDependency());
            });

            final NamedDomainObjectProvider<SourceSet> mainSourceSet = project.getExtensions().getByType(SourceSetContainer.class).named(SourceSet.MAIN_SOURCE_SET_NAME);
            minecraft.getRuns().configureEach(run -> run.classpath().from(mainSourceSet.map(SourceSet::getRuntimeClasspath), mainSourceSet.map(SourceSet::getOutput)));
        });

        this.createDisplayMinecraftVersions(minecraft, project.getTasks());
        project.afterEvaluate(p -> {
            if (minecraft.targetVersion().isPresent()) {
                final VersionDescriptor.Full version = minecraft.targetVersion().get();
                if (!version.download(DownloadClassifier.CLIENT_MAPPINGS).isPresent() && !version.download(DownloadClassifier.SERVER_MAPPINGS)
                    .isPresent()) {
                    throw new GradleException(String.format("Version '%s' specified in the 'minecraft' extension was released before Mojang "
                        + "provided official mappings! Try '%s' instead.", minecraft.version().get(),
                        minecraft.versions().latestVersion(VersionClassifier.RELEASE).orElse("<unknown>")));
                }
                p.getLogger().lifecycle(String.format("Targeting Minecraft '%s' on a '%s' platform", version.id(),
                    minecraft.platform().get().name()
                ));
            } else {
                throw new GradleException("No minecraft version has been set! Did you set the version() property in the 'minecraft' extension");
            }

            this.configureIDEIntegrations(
                project,
                p.getTasks().named(Constants.Tasks.PREPARE_WORKSPACE)
            );
        });
    }

    private void createDisplayMinecraftVersions(final MinecraftExtensionImpl extension, final TaskContainer tasks) {
        tasks.register("displayMinecraftVersions", DisplayMinecraftVersionsTask.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.setDescription("Displays all Minecraft versions that can be targeted");
            task.getVersions().set(task.getProject().getProviders().provider(() -> extension.versions().availableVersions()));
        });
    }

    private void configureIDEIntegrations(
        final Project project,
        final TaskProvider<?> prepareWorkspaceTask
    ) {
        project.getPlugins().apply(IdeaExtPlugin.class);
        project.getPlugins().apply(EclipsePlugin.class);

        IdeConfigurer.apply(project, new IdeConfigurer.IdeImportAction() {
            @Override
            public void idea(final Project project, final IdeaModel idea, final ProjectSettings ideaExtension) {
                // Navigate via the extension properties...
                // https://github.com/JetBrains/gradle-idea-ext-plugin/wiki
                final TaskTriggersConfig taskTriggers = ((ExtensionAware) ideaExtension).getExtensions().getByType(TaskTriggersConfig.class);

                // Automatically prepare a workspace after importing
                taskTriggers.afterSync(prepareWorkspaceTask);
            }

            @Override
            public void eclipse(final Project project, final EclipseModel eclipse) {
                eclipse.synchronizationTasks(prepareWorkspaceTask);
            }
        });
    }

}
