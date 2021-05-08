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
package org.spongepowered.gradle.vanilla.repository;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.MinecraftExtensionImpl;
import org.spongepowered.gradle.vanilla.repository.rule.JoinedProvidesClientAndServerRule;
import org.spongepowered.gradle.vanilla.repository.rule.MinecraftIvyModuleExtraDataApplierRule;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * The minimum plugin to add the minecraft repository to projects or globally.
 */
public class MinecraftRepositoryPlugin implements Plugin<Object> {

    /**
     * A variant of {@link IvyArtifactRepository#MAVEN_IVY_PATTERN} that takes
     * into account our metadata revision number.
     */
    public static final String IVY_METADATA_PATTERN = "[organisation]/[module]/[revision]/ivy-[revision]-vg" + MinecraftResolver.METADATA_VERSION + ".xml";

    private @Nullable Provider<MinecraftProviderService> service;

    @Override
    public void apply(final Object target) {
        if (target instanceof Project) {
            this.applyToProject((Project) target);
        } else if (target instanceof Settings) {
            this.applyToSettings((Settings) target);
        } else if (target instanceof Gradle) {
            // no-op marker left by our settings plugin
        } else {
            throw new IllegalArgumentException("Expected target to be a Project or Settings, but was a " + target.getClass());
        }
    }

    public Provider<MinecraftProviderService> service() {
        final @Nullable Provider<MinecraftProviderService> service = this.service;
        if (service == null) {
            throw new IllegalStateException("No service is available on this plugin");
        }
        return service;
    }

    private void applyToProject(final Project project) {
        // Setup
        final Path sharedCacheDirectory = MinecraftRepositoryPlugin.resolveCache(project.getGradle().getGradleUserHomeDir().toPath());
        final Path rootProjectCache = MinecraftRepositoryPlugin.resolveCache(project.getRootDir().toPath().resolve(".gradle"));
        final Provider<MinecraftProviderService> service = this.registerService(project.getGradle(), sharedCacheDirectory, rootProjectCache);

        // Apply vanillagradle caches
        if (!project.getGradle().getPlugins().hasPlugin(MinecraftRepositoryPlugin.class)) {
            this.createRepositories(project.getRepositories(), service, sharedCacheDirectory, rootProjectCache);
            this.registerComponentMetadataRules(project.getDependencies().getComponents());
        }

        // Register tool configurations
        for (final ResolvableTool tool : ResolvableTool.values()) {
            project.getConfigurations().register(tool.id(), config -> {
                config.defaultDependencies(deps -> {
                    deps.add(project.getDependencies().create(tool.notation()));
                });
            });
        }

        // Hook into resolution to provide the Minecraft artifact
        project.getConfigurations().configureEach(configuration -> this.configureResolutionStrategy(project.getLogger(), service, project, configuration.getResolutionStrategy()));
    }

    private void configureResolutionStrategy(final Logger logger, final Provider<MinecraftProviderService> service, final Project project, final ResolutionStrategy strategy) {
        JoinedProvidesClientAndServerRule.configureResolution(strategy.getCapabilitiesResolution());
        strategy.eachDependency(dependency -> {
            final ModuleVersionSelector dep = dependency.getTarget();
            if (MinecraftPlatform.GROUP.equals(dep.getGroup())) {
                final String[] components = dep.getName().split("_", 2); // any manually specified components will exist, but can't be resolved
                if (components.length == 0) {
                    return; // failed
                }
                final Optional<MinecraftPlatform> platform = MinecraftPlatform.byId(components[0]);
                if (!platform.isPresent()) {
                    return;
                }

                final @Nullable String version = dep.getVersion();
                logger.info("Attempting to resolve minecraft {} version {}", dep.getName(), version);
                if (version != null) {
                    try {
                        final MinecraftResolver resolver = service.get().resolver(project);
                        final @Nullable MinecraftExtensionImpl extension = (MinecraftExtensionImpl) project.getExtensions().findByType(MinecraftExtension.class);
                        if (extension != null) {
                            final ResolutionResult<MinecraftResolver.MinecraftEnvironment> env = resolver.provide(platform.get(), version, extension.modifiers()).get();
                            if (env.isPresent()) {
                                dependency.useTarget(MinecraftPlatform.GROUP + ':' + env.get().decoratedArtifactId() + ':' + version);
                            }
                        } else {
                            // Request the appropriate jar, block until it's provided
                            resolver.provide(platform.get(), version).get();
                        }
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (final ExecutionException ex) {
                        // log exception but don't throw, dependency resolution failure will come when expected.
                        logger.error("Failed to resolve Minecraft {} version {}:", platform.get(), version, ex.getCause());
                    }
                }
            }
        });
    }

    private void applyToSettings(final Settings settings) {
        // Setup
        final Path sharedCacheDirectory = MinecraftRepositoryPlugin.resolveCache(settings.getGradle().getGradleUserHomeDir().toPath());
        final Path rootProjectCache = MinecraftRepositoryPlugin.resolveCache(settings.getRootDir().toPath().resolve(".gradle"));
        final Provider<MinecraftProviderService> service = this.registerService(settings.getGradle(), sharedCacheDirectory, rootProjectCache);

        // Apply VanillaGradle caches
        this.createRepositories(settings.getDependencyResolutionManagement().getRepositories(), service, sharedCacheDirectory, rootProjectCache);
        this.registerComponentMetadataRules(settings.getDependencyResolutionManagement().getComponents());

        final MinecraftRepositoryExtension extension = this.registerExtension(settings, service, settings.getRootDir());

        // Leave a marker so projects don't try to override these
        settings.getGradle().getPluginManager().apply(MinecraftRepositoryPlugin.class);

        // Once the user Settings file has gone through, register our repositories if it makes sense to
        settings.getGradle().settingsEvaluated(s -> {
            if (extension.injectRepositories().get()) {
                Constants.Repositories.applyTo(s.getDependencyResolutionManagement().getRepositories());
            }
        });
    }

    // Common handling //

    private MinecraftRepositoryExtension registerExtension(final ExtensionAware holder, final Provider<MinecraftProviderService> service, final File rootdir) {
        final MinecraftRepositoryExtensionImpl
            extension = (MinecraftRepositoryExtensionImpl) holder.getExtensions().create(MinecraftRepositoryExtension.class, "minecraft", MinecraftRepositoryExtensionImpl.class);
        extension.providerService.set(service);
        extension.baseDir.set(rootdir);
        return extension;
    }

    private static Path resolveCache(final Path root) {
        return root.resolve(Constants.Directories.CACHES).resolve(Constants.NAME).resolve("v" + MinecraftResolver.STORAGE_VERSION);
    }

    private void createRepositories(final RepositoryHandler repositories, final Provider<MinecraftProviderService> service, final Path sharedCache, final Path rootProjectCache) {
        // Global cache (for standard artifacts)
        repositories.ivy(MinecraftRepositoryPlugin.repositoryConfiguration(
            "VanillaGradle Global Cache",
            sharedCache.resolve(Constants.Directories.JARS),
            service
        ));
        // Root-project cache (for project-specific transformations, such as access wideners, potentially other things
        repositories.ivy(MinecraftRepositoryPlugin.repositoryConfiguration(
            "VanillaGradle Project Cache",
            rootProjectCache.resolve(Constants.Directories.JARS),
            service
        ));
    }

    private static Action<IvyArtifactRepository> repositoryConfiguration(final String name, final Path root, final Provider<MinecraftProviderService> service) {
       return ivy -> {
           ivy.setName(name);
           ivy.setUrl(root.toUri());
           ivy.patternLayout(layout -> {
               layout.artifact(IvyArtifactRepository.MAVEN_ARTIFACT_PATTERN);
               layout.ivy(MinecraftRepositoryPlugin.IVY_METADATA_PATTERN);
               layout.setM2compatible(true);
           });
           ivy.content(content -> {
               for (final MinecraftPlatform platform : MinecraftPlatform.all()) {
                   content.includeModuleByRegex(
                       Pattern.quote(MinecraftPlatform.GROUP),
                       Pattern.quote(platform.artifactId()) + "($|_.+)" // Allow both the literal artifact ID, plus any encoded modifiers
                   );
               }
           });
           ivy.setComponentVersionsLister(LauncherMetaVersionLister.class, params -> params.params(service));
           ivy.setMetadataSupplier(LauncherMetaMetadataSupplier.class, params -> params.params(service));
           ivy.setAllowInsecureProtocol(true);
           ivy.getResolve().setDynamicMode(false);
           ivy.metadataSources(sources -> {
               sources.ivyDescriptor();
               sources.artifact();
           });
       };
    }

    private void registerComponentMetadataRules(final ComponentMetadataHandler handler) {
        handler.withModule(MinecraftPlatform.JOINED.moduleName(), JoinedProvidesClientAndServerRule.class);

        for (final MinecraftPlatform platform : MinecraftPlatform.all()) {
            handler.withModule(platform.moduleName(), MinecraftIvyModuleExtraDataApplierRule.class);
        }
    }

    private Provider<MinecraftProviderService> registerService(final Gradle gradle, final Path sharedCacheDir, final Path rootProjectCacheDir) {
        return this.service = gradle.getSharedServices().registerIfAbsent("vanillaGradleMinecraft", MinecraftProviderService.class, params -> {
            final MinecraftProviderService.Parameters options = params.getParameters();
            options.getSharedCache().set(sharedCacheDir.toFile());
            options.getRootProjectCache().set(rootProjectCacheDir.toFile());
            options.getOfflineMode().set(gradle.getStartParameter().isOffline());
            options.getRefreshDependencies().set(gradle.getStartParameter().isRefreshDependencies());
        });
    }
}
