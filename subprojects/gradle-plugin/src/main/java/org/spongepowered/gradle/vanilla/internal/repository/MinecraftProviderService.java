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
package org.spongepowered.gradle.vanilla.internal.repository;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.model.VersionManifestRepository;
import org.spongepowered.gradle.vanilla.resolver.apache.ApacheHttpDownloader;
import org.spongepowered.gradle.vanilla.resolver.Downloader;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolverImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class MinecraftProviderService implements
    BuildService<MinecraftProviderService.Parameters>,
    AutoCloseable,
    OperationCompletionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftProviderService.class);

    private volatile @Nullable Downloader downloader;
    private volatile @Nullable MinecraftResolverImpl resolver;
    private volatile @Nullable VersionManifestRepository versions;
    private final ExecutorService executor;
    private final ThreadLocal<ResolverState> activeState = ThreadLocal.withInitial(ResolverState::new);

    public interface Parameters extends BuildServiceParameters {
        DirectoryProperty getSharedCache(); // global cache
        DirectoryProperty getRootProjectCache(); // root project cache, used for any transformed artifacts that are reliant on project data
        Property<Boolean> getOfflineMode(); // gradle -o offline mode parameter, only resolve from local cache
        Property<Boolean> getRefreshDependencies(); // gradle --refresh-dependencies start parameter, ignore existing data in local cache
    }

    public MinecraftProviderService() {
        final int availableCpus = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newWorkStealingPool(Math.min(Math.max(4, availableCpus * 2), 64));
        MinecraftProviderService.LOGGER.info(Constants.NAME + ": Creating minecraft provider service");
    }

    @Override
    public void onFinish(final FinishEvent finishEvent) {
        // no-op, a workaround to keep the service alive for the entire build
        // see https://github.com/diffplug/spotless/pull/720#issuecomment-713399731
    }

    /**
     * Prepare the resolver to receive a resolution request from a context.
     *
     * <p>This is intended for situations where artifact resolution needs to
     * step through multiple hooks within Gradle to gather all necessary
     * information. We track these bits of information, to be consumed at the
     * next artifact resolution.</p>
     *
     * @param project the project to use for resolving dependencies
     * @param modifiers the artifact modifiers to apply to the eventual output artifact
     */
    public void primeResolver(final Project project, final Set<ArtifactModifier> modifiers) {
        final ResolverState state = this.activeState.get();
        state.configurationSource = project.getConfigurations();
        state.modifiers = modifiers;
    }

    public Set<ArtifactModifier> peekModifiers() {
        final ResolverState state = this.activeState.get();
        final @Nullable Set<ArtifactModifier> modifiers = state.modifiers;
        if (modifiers == null) {
            throw new GradleException("No artifact modifiers were staged for resolution operation!");
        }
        return modifiers;
    }

    public void dropState() {
        this.activeState.remove();
    }

    public Downloader downloader() {
        @Nullable Downloader downloader = this.downloader;
        if (downloader == null) {
            synchronized (this) {
                if (this.downloader == null) {
                    final Parameters params = this.getParameters();
                    final ApacheHttpDownloader.ResolveMode mode;
                    if (params.getOfflineMode().get()) {
                        mode = Downloader.ResolveMode.LOCAL_ONLY;
                    } else if (params.getRefreshDependencies().get()) {
                        mode = Downloader.ResolveMode.REMOTE_ONLY;
                    } else {
                        mode = Downloader.ResolveMode.LOCAL_THEN_REMOTE;
                    }
                    this.downloader = downloader = new ApacheHttpDownloader(
                        this.executor,
                        this.getParameters().getSharedCache().get().getAsFile().toPath(),
                        mode
                    );
                } else {
                    return this.downloader;
                }
            }
        }
        return downloader;
    }

    public MinecraftResolver resolver() {
        @Nullable MinecraftResolverImpl resolver = this.resolver;
        if (resolver == null) {
            synchronized (this) {
                if (this.resolver == null) {
                    this.resolver = resolver = new MinecraftResolverImpl(
                        this.versions(),
                        this.downloader().withBaseDir(this.downloader().baseDir().resolve(Constants.Directories.JARS)),
                        this.getParameters().getRootProjectCache().get().getAsFile().toPath().resolve(Constants.Directories.JARS),
                        this.executor,
                        this::resolveTool,
                        this.getParameters().getRefreshDependencies().get()
                    );
                } else {
                    return this.resolver;
                }
            }
        }
        return resolver;
    }

    private URL[] resolveTool(final ResolvableTool tool) {
        final @Nullable ConfigurationContainer configurations = this.activeState.get().configurationSource;
        if (configurations == null) {
            throw new IllegalArgumentException("Tried to perform a configuration resolution outside of a project-managed context!");
        }
        return configurations.getByName(tool.id()).resolve().stream()
            .map(file -> {
                try {
                    return file.toURI().toURL();
                } catch (final MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
            })
            .toArray(URL[]::new);
    }

    public VersionManifestRepository versions() {
        @Nullable VersionManifestRepository versions = this.versions;
        if (versions == null) {
            synchronized (this) {
                if (this.versions == null) {
                    // Create a version repository. Offline and --refresh-dependencies are handled by our overall Downloader
                    final Path cacheDir = this.getParameters().getSharedCache().get().getAsFile().toPath().resolve(Constants.Directories.MANIFESTS);
                    this.versions = versions = VersionManifestRepository.fromDownloader(this.downloader().withBaseDir(cacheDir));
                } else {
                    return this.versions;
                }
            }
        }
        return versions;
    }

    @Override
    public void close() throws IOException {
        MinecraftProviderService.LOGGER.info(Constants.NAME + ": Shutting down MinecraftProviderService");
        this.executor.shutdown();
        boolean success;
        try {
            success = this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
            success = false;
        }

        if (!success) {
            MinecraftProviderService.LOGGER.warn(Constants.NAME + ": Failed to shut down executor in 10 seconds, forcing shutdown!");
            this.executor.shutdownNow();
        }

        final @Nullable Downloader downloader = this.downloader;
        this.downloader = null;
        if (downloader != null) {
            downloader.close();
        }
    }

    static final class ResolverState {

        @MonotonicNonNull ConfigurationContainer configurationSource;
        @Nullable Set<ArtifactModifier> modifiers;

    }

}
