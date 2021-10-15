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
package org.spongepowered.gradle.vanilla.internal;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.util.ConfigureUtil;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.internal.model.VersionClassifier;
import org.spongepowered.gradle.vanilla.internal.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.CSrgMappingFormat;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.ObfMappingsEntry;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.OfficialMappingsEntry;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.ParchmentMappingFormat;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.ProGuardMappingFormat;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.SrgMappingFormat;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.TSrgMappingFormat;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.TinyMappingFormat;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.XSrgMappingFormat;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.MappingsModifier;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingFormat;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsContainer;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.internal.repository.MinecraftProviderService;
import org.spongepowered.gradle.vanilla.internal.repository.MinecraftRepositoryPlugin;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.AccessWidenerModifier;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.runs.RunConfiguration;
import org.spongepowered.gradle.vanilla.runs.RunConfigurationContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

public class MinecraftExtensionImpl implements MinecraftExtension {

    // User-set properties
    private final Provider<MinecraftProviderService> providerService;
    private final Property<String> version;
    private final Property<MinecraftPlatform> platform;
    private final Property<Boolean> injectRepositories;
    private final PolymorphicDomainObjectContainer<MappingFormat<@NonNull ?>> mappingFormats;
    private final MappingsContainer mappings;
    private final Property<String> minecraftMappings;
    private final DirectoryProperty sharedCache;
    private final DirectoryProperty projectCache;
    private final ConfigurableFileCollection accessWideners;

    // Derived properties
    private final Property<VersionDescriptor.Full> targetVersion;
    private final DirectoryProperty assetsDirectory;

    // Internals
    private final Project project;
    private final RunConfigurationContainer runConfigurations;
    private volatile List<ArtifactModifier> lazyModifiers;

    @SuppressWarnings("unchecked")
    @Inject
    public MinecraftExtensionImpl(final Gradle gradle, final ObjectFactory factory, final Project project, final Provider<MinecraftProviderService> providerService) {
        this.project = project;
        this.providerService = providerService;
        this.version = factory.property(String.class);
        this.platform = factory.property(MinecraftPlatform.class).convention(MinecraftPlatform.JOINED);
        this.injectRepositories = factory.property(Boolean.class).convention(project.provider(() -> !gradle.getPlugins().hasPlugin(MinecraftRepositoryPlugin.class))); // only inject if we aren't already in Settings
        this.mappingFormats = factory.polymorphicDomainObjectContainer((Class<MappingFormat<@NonNull ?>>) (Class<?>) MappingFormat.class);
        this.mappings = new MappingsContainer(project, this);
        this.minecraftMappings = factory.property(String.class).convention(OfficialMappingsEntry.NAME);
        this.accessWideners = factory.fileCollection();

        this.mappingFormats.add(new ProGuardMappingFormat());
        this.mappingFormats.add(new TinyMappingFormat());
        this.mappingFormats.add(new ParchmentMappingFormat());
        this.mappingFormats.add(new SrgMappingFormat());
        this.mappingFormats.add(new TSrgMappingFormat());
        this.mappingFormats.add(new CSrgMappingFormat());
        this.mappingFormats.add(new XSrgMappingFormat());
        this.mappings.add(new ObfMappingsEntry(project, this));
        this.mappings.add(new OfficialMappingsEntry(project, this));


        this.assetsDirectory = factory.directoryProperty();
        this.sharedCache = factory.directoryProperty().convention(providerService.flatMap(it -> it.getParameters().getSharedCache()));
        this.sharedCache.disallowChanges();
        this.projectCache = factory.directoryProperty().convention(providerService.flatMap(it -> it.getParameters().getRootProjectCache()));
        this.projectCache.disallowChanges();

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
            this.assetsDirectory.set(this.sharedCache.map(dir -> dir.dir(Constants.Directories.ASSETS)));
        }

        this.targetVersion = factory.property(VersionDescriptor.Full.class)
            .value(this.version.zip(this.providerService, (version, service) -> {
                try {
                    return service.versions().fullVersion(version).get()
                        .orElseThrow(() -> {
                            try {
                                return new GradleException(String.format("Version '%s' specified in the 'minecraft' extension was not found in the "
                                    + "manifest! Try '%s' instead.", this.version.get(), this.providerService.get().versions().latestVersion(VersionClassifier.RELEASE).get().orElse(null)));
                            } catch (final InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(); // todo???
                            } catch (final ExecutionException ex) {
                                throw new GradleException("Failed to fetch latest version", ex.getCause());
                            }
                        });
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
    public String injectVersion(final String file) {
        Objects.requireNonNull(file, "file");
        return this.injectVersion(this.project.file(file));
    }

    @Override
    public String injectVersion(final File file) {
        Objects.requireNonNull(file, "file");
        try {
            return  this.providerService.get().versions().inject(file);
        } catch (final IOException ex) {
            throw new GradleException("Failed to read injected version manifest from " + file, ex);
        }
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
    public void accessWideners(final Object... files) {
        this.accessWideners.from(files);
    }

    public ConfigurableFileCollection accessWideners() {
        return this.accessWideners;
    }

    @Override
    public PolymorphicDomainObjectContainer<MappingFormat<@NonNull ?>> getMappingFormats() {
        return mappingFormats;
    }

    @Override
    public void mappingFormats(Action<NamedDomainObjectSet<MappingFormat<@NonNull ?>>> configure) {
        configure.execute(mappingFormats);
    }

    @Override
    public void mappingFormats(@DelegatesTo(value = NamedDomainObjectSet.class, strategy = Closure.DELEGATE_FIRST) Closure<NamedDomainObjectSet<MappingFormat<@NonNull ?>>> configureClosure) {
        ConfigureUtil.configure(configureClosure, mappingFormats);
    }

    @Override
    public MappingsContainer getMappings() {
        return mappings;
    }

    @Override
    public void mappings(Action<MappingsContainer> configure) {
        configure.execute(mappings);
    }

    @Override
    public void mappings(@DelegatesTo(value = MappingsContainer.class, strategy = Closure.DELEGATE_FIRST) Closure<MappingsContainer> configureClosure) {
        ConfigureUtil.configure(configureClosure, mappings);
    }

    @Override
    public Property<String> minecraftMappings() {
        return minecraftMappings;
    }

    @Override
    public void minecraftMappings(MappingsEntry mappings) {
        minecraftMappings(mappings.getName());
    }

    @Override
    public void minecraftMappings(String mappings) {
        this.minecraftMappings.set(mappings);
    }

    public synchronized List<ArtifactModifier> modifiers() {
        if (this.lazyModifiers == null) {
            final List<ArtifactModifier> modifiers = new ArrayList<>();

            if (!minecraftMappings.get().equals(ObfMappingsEntry.NAME)) {
                modifiers.add(new MappingsModifier(mappings, ObfMappingsEntry.NAME, minecraftMappings.get()));
            }

            this.accessWideners.disallowChanges();
            if (!this.accessWideners.isEmpty()) {
                modifiers.add(new AccessWidenerModifier(this.accessWideners.getFiles()));
            }
            return this.lazyModifiers = Collections.unmodifiableList(modifiers);
        }
        return this.lazyModifiers;
    }

    DirectoryProperty projectCache() {
        return this.projectCache;
    }

    DirectoryProperty sharedCache() {
        return this.sharedCache;
    }

    public DirectoryProperty assetsDirectory() {
        return this.assetsDirectory;
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

}
