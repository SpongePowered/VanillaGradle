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

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.ConfigureUtil;
import org.spongepowered.gradle.vanilla.model.VersionClassifier;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.model.VersionManifestRepository;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftProviderService;
import org.spongepowered.gradle.vanilla.runs.RunConfiguration;
import org.spongepowered.gradle.vanilla.runs.RunConfigurationContainer;
import org.spongepowered.gradle.vanilla.task.AccessWidenJarTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

public abstract class MinecraftExtensionImpl implements MinecraftExtension {

    private final Project project;
    private final Provider<MinecraftProviderService> providerService;
    private final Property<String> version;
    private final Property<MinecraftPlatform> platform;
    private final Property<Boolean> injectRepositories;

    private final Property<VersionDescriptor.Full> targetVersion;

    private final DirectoryProperty assetsDirectory;
    private final DirectoryProperty remappedDirectory;
    private final DirectoryProperty originalDirectory;
    private final DirectoryProperty mappingsDirectory;
    private final DirectoryProperty filteredDirectory;
    private final DirectoryProperty decompiledDirectory;
    private final DirectoryProperty accessWidenedDirectory;
    private final Path sharedCache;
    private final Path projectCache;

    private final RunConfigurationContainer runConfigurations;

    @Inject
    public MinecraftExtensionImpl(final Gradle gradle, final ObjectFactory factory, final Project project, final Provider<MinecraftProviderService> providerService) {
        this.project = project;
        this.providerService = providerService;
        this.version = factory.property(String.class);
        this.platform = factory.property(MinecraftPlatform.class).convention(MinecraftPlatform.JOINED);
        this.injectRepositories = factory.property(Boolean.class).convention(true);
        this.assetsDirectory = factory.directoryProperty();
        this.remappedDirectory = factory.directoryProperty();
        this.originalDirectory = factory.directoryProperty();
        this.mappingsDirectory = factory.directoryProperty();
        this.filteredDirectory = factory.directoryProperty();
        this.decompiledDirectory = factory.directoryProperty();
        this.accessWidenedDirectory = factory.directoryProperty();

        final Path gradleHomeDirectory = gradle.getGradleUserHomeDir().toPath();
        final Path cacheDirectory = gradleHomeDirectory.resolve(Constants.Directories.CACHES);
        final Path rootDirectory = cacheDirectory.resolve(Constants.NAME);
        final Path globalJarsDirectory = rootDirectory.resolve(Constants.Directories.JARS);
        final Path projectLocalJarsDirectory = project.getProjectDir().toPath().resolve(".gradle").resolve(Constants.Directories.CACHES)
                .resolve(Constants.NAME).resolve(Constants.Directories.JARS);

        // Test common assets directory locations and use those instead, if they exist
        boolean found = false;
        for (final Path candidate : Constants.Directories.SHARED_ASSET_LOCATIONS) {
            if (Files.isDirectory(candidate)) {
                this.assetsDirectory.set(candidate.toFile());
                found = true;
                break;
            }
        }
        if (!found) {
            this.assetsDirectory.set(rootDirectory.resolve(Constants.Directories.ASSETS).toFile());
        }

        this.originalDirectory.set(globalJarsDirectory.resolve(Constants.Directories.ORIGINAL).toFile());
        this.mappingsDirectory.set(rootDirectory.resolve(Constants.Directories.MAPPINGS).toFile());
        this.remappedDirectory.set(projectLocalJarsDirectory.resolve(Constants.Directories.REMAPPED).toFile());
        this.filteredDirectory.set(projectLocalJarsDirectory.resolve(Constants.Directories.FILTERED).toFile());
        this.decompiledDirectory.set(projectLocalJarsDirectory.resolve(Constants.Directories.DECOMPILED).toFile());
        this.accessWidenedDirectory.set(projectLocalJarsDirectory.resolve(Constants.Directories.ACCESS_WIDENED).toFile());
        this.projectCache = projectLocalJarsDirectory;
        this.sharedCache = rootDirectory;

        final Path cacheDir = rootDirectory.resolve(Constants.Directories.MANIFESTS);
        // Create a version repository. If Gradle is in offline mode, read only from cache
        /*if (project.hasProperty(Constants.Manifests.SKIP_CACHE)) {
            this.versions = VersionManifestRepository.direct();
        } else {
            this.versions = VersionManifestRepository.caching(cacheDir, !gradle.getStartParameter().isOffline());
        }*/
        this.targetVersion = factory.property(VersionDescriptor.Full.class)
            .value(this.version.zip(this.providerService, (version, service) -> {
                try {
                    return service.versions().fullVersion(version).get();
                        /*.orElseThrow(() -> new GradleException(String.format("Version '%s' specified in the 'minecraft' extension was not found in the "
                            + "manifest! Try '%s' instead.", this.version.get(), this.versions.latestVersion(VersionClassifier.RELEASE).get().orElse(null))));*/
                } catch (final InterruptedException | ExecutionException ex) {
                    throw new GradleException("Failed to read version manifest", ex);
                }
            }));
        this.targetVersion.finalizeValueOnRead();

        this.runConfigurations = factory.newInstance(RunConfigurationContainer.class, factory.domainObjectContainer(RunConfiguration.class), this);
    }

    @Override
    public Property<Boolean> injectRepositories() {
        return this.injectRepositories;
    }

    @Override
    public void injectRepositories(final boolean injectRepositories) {
        this.injectRepositories.set(injectRepositories);
    }

    @Override
    public Property<String> version() {
        return this.version;
    }

    @Override
    public void version(final String version) {
        this.version.set(version);
    }

    @Override
    public void injectedVersion(final Object versionFile) {
        final File resolved = this.project.file(versionFile);
        try {
            final String versionId = this.providerService.get().versions().inject(resolved);
            this.version.set(versionId);
        } catch (final IOException ex) {
            throw new GradleException("Failed to read injected version manifest from " + resolved, ex);
        }
    }

    @Override
    public void latestRelease() {
        this.version.set(this.providerService.map(service -> {
            try {
                return service.versions().latestVersion(VersionClassifier.RELEASE).get()
                    .orElseThrow(() -> new IllegalArgumentException("Could not find any latest release!"));
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException();
            } catch (final ExecutionException ex) {
                throw new GradleException("Failed to fetch latest release", ex);
            }
        }));
    }

    @Override
    public void latestSnapshot() {
        this.version.set(this.providerService.map(service -> {
            try {
                return service.versions().latestVersion(VersionClassifier.SNAPSHOT).get()
                    .orElseThrow(() -> new IllegalArgumentException("Could not find any latest release!"));
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException();
            } catch (final ExecutionException ex) {
                throw new GradleException("Failed to fetch latest release", ex);
            }
        }));
    }

    @Override
    public Property<MinecraftPlatform> platform() {
        return this.platform;
    }

    @Override
    public void platform(final MinecraftPlatform platform) {
        this.platform.set(platform);
    }

    @Override
    public void accessWidener(final Object file) {
        final TaskProvider<AccessWidenJarTask> awTask;
        final TaskContainer tasks = this.project.getTasks();
        if (tasks.getNames().contains(Constants.Tasks.ACCESS_WIDENER)) {
            awTask = tasks.named(Constants.Tasks.ACCESS_WIDENER, AccessWidenJarTask.class);
        } else {
            final Configuration accessWidener = this.project.getConfigurations().maybeCreate(Constants.Configurations.ACCESS_WIDENER);
            accessWidener.defaultDependencies(deps -> deps.add(this.project.getDependencies().create(Constants.WorkerDependencies.ACCESS_WIDENER)));
            final FileCollection widenerClasspath = accessWidener.getIncoming().getFiles();
            final DirectoryProperty destinationDir = this.accessWidenedDirectory;

            awTask = tasks.register(Constants.Tasks.ACCESS_WIDENER, AccessWidenJarTask.class, task -> {
                task.setWorkerClasspath(widenerClasspath);
                task.getExpectedNamespace().set("named");
                task.getDestinationDirectory().set(destinationDir);
            });
        }

        awTask.configure(task -> task.getAccessWideners().from(file));
    }

    Path projectCache() {
        return this.projectCache;
    }

    Path sharedCache() {
        return this.sharedCache;
    }

    public DirectoryProperty assetsDirectory() {
        return this.assetsDirectory;
    }

    protected DirectoryProperty remappedDirectory() {
        return this.remappedDirectory;
    }

    protected DirectoryProperty originalDirectory() {
        return this.originalDirectory;
    }

    protected DirectoryProperty mappingsDirectory() {
        return this.mappingsDirectory;
    }

    protected DirectoryProperty filteredDirectory() {
        return this.filteredDirectory;
    }

    protected DirectoryProperty decompiledDirectory() {
        return this.decompiledDirectory;
    }

    @Override
    public RunConfigurationContainer getRuns() {
        return this.runConfigurations;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void runs(@DelegatesTo(value = RunConfigurationContainer.class, strategy = Closure.DELEGATE_FIRST) final Closure run) {
        ConfigureUtil.configure(run, this.runConfigurations);
    }

    @Override
    public void runs(final Action<RunConfigurationContainer> run) {
        Objects.requireNonNull(run, "run").execute(this.runConfigurations);
    }

    public Provider<VersionDescriptor.Full> targetVersion() {
        return this.targetVersion;
    }

    protected void populateMinecraftClasspath() {
        /*this.minecraftClasspath.configure(config -> config.withDependencies(a -> {
        }));*/
    }

    @Override
    public Dependency minecraftDependency() {
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("path", this.project.getPath());
        parameters.put("configuration", Constants.Configurations.MINECRAFT);
        return this.project.getDependencies().project(parameters);
    }
}
