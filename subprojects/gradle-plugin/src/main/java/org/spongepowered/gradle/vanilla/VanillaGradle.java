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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
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
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.MinecraftExtensionImpl;
import org.spongepowered.gradle.vanilla.internal.ProvideMinecraftPlugin;
import org.spongepowered.gradle.vanilla.internal.ResolveMinecraftLibNames;
import org.spongepowered.gradle.vanilla.internal.ShadowConfigurationApplier;
import org.spongepowered.gradle.vanilla.internal.repository.MinecraftProviderService;
import org.spongepowered.gradle.vanilla.internal.repository.MinecraftRepositoryPlugin;
import org.spongepowered.gradle.vanilla.internal.util.ConfigurationUtils;
import org.spongepowered.gradle.vanilla.internal.util.IdeConfigurer;
import org.spongepowered.gradle.vanilla.internal.util.SelfPreferringClassLoader;
import org.spongepowered.gradle.vanilla.task.DisplayMinecraftVersionsTask;
import org.spongepowered.gradle.vanilla.task.DumpClassTask;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * A plugin that actually adds the provided Minecraft artifacts to the project classpath.
 */
public final class VanillaGradle implements Plugin<Object> {

    private static final String SHADOW_JAR_TASK_CLASS_NAME = "com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar";
    private static final List<String> SHADOW_PLUGIN_IDS = Arrays.asList(
            "com.github.johnrengelman.shadow",
            "io.github.goooler.shadow"
    );
    private static final AtomicBoolean VERSION_ANNOUNCED = new AtomicBoolean();

    @Override
    public void apply(final @NonNull Object target) {
        if (target instanceof Settings) {
            ((Settings) target).getPlugins().apply(MinecraftRepositoryPlugin.class);
        } else if (target instanceof Project) {
            this.applyToProject((Project) target);
        } else {
            throw new IllegalArgumentException("Unknown target '" + target + "' of type " + target.getClass());
        }
    }

    private void applyToProject(final Project project) {
        if (VanillaGradle.VERSION_ANNOUNCED.compareAndSet(false, true)) {
            project.getLogger().lifecycle(String.format("SpongePowered Vanilla 'GRADLE' Toolset Version '%s'", Constants.VERSION));
        }

        project.getPlugins().apply(ProvideMinecraftPlugin.class);
        final MinecraftExtensionImpl minecraft = (MinecraftExtensionImpl) project.getExtensions().getByType(MinecraftExtension.class);
        final NamedDomainObjectProvider<Configuration> minecraftConfig = project.getConfigurations().named(Constants.Configurations.MINECRAFT);
        project.getPlugins().withType(JavaPlugin.class, plugin -> {
            Stream.of(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).forEach(config -> {
                project.getConfigurations().named(config, c -> {
                    c.extendsFrom(minecraftConfig.get());
                });
            });

            project.afterEvaluate(p -> {
                final NamedDomainObjectProvider<SourceSet> mainSourceSet =
                    p.getExtensions().getByType(SourceSetContainer.class).named(SourceSet.MAIN_SOURCE_SET_NAME);
                minecraft.getRuns().configureEach(
                    run -> run.getClasspath().from(mainSourceSet.map(SourceSet::getOutput), mainSourceSet.map(SourceSet::getRuntimeClasspath)));
            });
        });

        for (final String shadowPluginId : SHADOW_PLUGIN_IDS) {
            project.getPlugins().withId(shadowPluginId, plugin -> {
                final Provider<Set<String>> provider = project.provider(new ResolveMinecraftLibNames(minecraftConfig));
                VanillaGradle.applyShadowConfiguration(project.getTasks(), provider, plugin);
            });
        }

        this.createDumpClass(project, minecraftConfig);
        this.createDisplayMinecraftVersions(project.getPlugins().getPlugin(MinecraftRepositoryPlugin.class).service(), project.getTasks());
        project.afterEvaluate(p -> {
            if (minecraft.targetVersion().isPresent()) {
                p.getLogger().lifecycle(String.format("Targeting Minecraft '%s' on a '%s' platform", minecraft.version().get(),
                    minecraft.platform().get().name()
                ));
            } else {
                throw new GradleException("No minecraft version has been set! Did you set the version() property in the 'minecraft' extension");
            }

            this.configureIDEIntegrations(
                project,
                minecraft.needsPrepareWorkspace(),
                p.getTasks().named(Constants.Tasks.PREPARE_WORKSPACE)
            );
        });
    }

    private void createDumpClass(
        final Project project, final NamedDomainObjectProvider<Configuration> minecraftConfig
    ) {
        final NamedDomainObjectProvider<Configuration> asmUtilConfiguration = project.getConfigurations().register(Constants.Configurations.CLASS_DUMP, config -> {
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
            config.defaultDependencies(deps -> {
                deps.add(project.getDependencies().create(Constants.WorkerDependencies.ASM_UTIL));
            });
            ConfigurationUtils.markAsJavaRuntime(project.getObjects(), config);
        });
        project.getTasks().register("dumpClass", DumpClassTask.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.getMinecraftClasspath().from(minecraftConfig.get());
            task.getAsmUtilClasspath().from(asmUtilConfiguration);
        });
    }

    private void createDisplayMinecraftVersions(final Provider<MinecraftProviderService> providerService, final TaskContainer tasks) {
        tasks.register("displayMinecraftVersions", DisplayMinecraftVersionsTask.class, task -> {
            task.setGroup(Constants.TASK_GROUP);
            task.setDescription("Displays all Minecraft versions that can be targeted");
            task.getMinecraftProvider().set(providerService);
        });
    }

    private void configureIDEIntegrations(
        final Project project,
        final Provider<Boolean> shouldRunPrepare,
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
                if (shouldRunPrepare.get()) {
                    taskTriggers.afterSync(prepareWorkspaceTask);
                }
            }

            @Override
            public void eclipse(final Project project, final EclipseModel eclipse) {
                if (shouldRunPrepare.get()) {
                    eclipse.synchronizationTasks(prepareWorkspaceTask);
                }
            }
        });
    }

    private static void applyShadowConfiguration(final TaskContainer tasks, final Provider<Set<String>> minecraftNames, final Plugin<?> shadowPlugin) {
        // Gradle seems to have some sort of classloader isolation................
        // When VanillaGradle is on the root project classpath and also used in
        // subprojects, but the shadow plugin is only applied in subprojects,
        // the shadow plugin classloader will be a separate child loader of the
        // class loader containing VanillaGradle.

        try {
            Class.forName(VanillaGradle.SHADOW_JAR_TASK_CLASS_NAME);
            ShadowConfigurationApplier.actuallyApplyShadowConfiguration(tasks, minecraftNames);
        } catch (final ClassNotFoundException ex) {
            // Isolation
            try (final URLClassLoader classLoader = new SelfPreferringClassLoader(
                new URL[]{VanillaGradle.class.getProtectionDomain().getCodeSource().getLocation()},
                shadowPlugin.getClass().getClassLoader()
            )) {
                Class.forName(ShadowConfigurationApplier.class.getName(), true, classLoader)
                    .getDeclaredMethod("actuallyApplyShadowConfiguration", TaskContainer.class, Provider.class)
                    .invoke(null, tasks, minecraftNames);
            } catch (final IOException | ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex2) {
                throw new GradleException("Failed to configure shadow plugin integration", ex2);
            }
        }
    }

}
