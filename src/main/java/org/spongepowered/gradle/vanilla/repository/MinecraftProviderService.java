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
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.model.VersionManifestRepository;
import org.spongepowered.gradle.vanilla.network.ApacheHttpDownloader;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public abstract class MinecraftProviderService implements BuildService<MinecraftProviderService.Parameters>, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftProviderService.class);

    private volatile @Nullable ApacheHttpDownloader downloader;
    private volatile @Nullable MinecraftResolverImpl resolver;
    private volatile @Nullable VersionManifestRepository versions;
    private final ExecutorService executor;

    public interface Parameters extends BuildServiceParameters {
        DirectoryProperty getSharedCache();
        DirectoryProperty getRootProjectCache();
        Property<Boolean> getOfflineMode();
        Property<Boolean> getRefreshDependencies();
    }

    public MinecraftProviderService() {
        // aaa
        this.executor = Executors.newWorkStealingPool();
        MinecraftProviderService.LOGGER.warn("Creating minecraft provider service");
    }

    public ApacheHttpDownloader downloader() {
        @Nullable ApacheHttpDownloader downloader = this.downloader;
        if (downloader == null) {
            synchronized (this) {
                if (this.downloader == null) {
                    this.downloader = downloader = new ApacheHttpDownloader(this.getParameters().getSharedCache().get().getAsFile().toPath());
                } else {
                    return this.downloader;
                }
            }
        }
        return downloader;
    }

    public MinecraftResolver resolver(final Project project) {
        @Nullable MinecraftResolverImpl resolver = this.resolver;
        if (resolver == null) {
            synchronized (this) {
                if (this.resolver == null) {
                    this.resolver = resolver = new MinecraftResolverImpl(
                        this.getParameters().getSharedCache().get().getAsFile().toPath(),
                        this.versions(),
                        this.downloader(),
                        this.executor
                    );
                } else {
                    return this.resolver.withResolver(this.toolResolver(project));
                }
            }
        }
        return resolver.withResolver(this.toolResolver(project));
    }

    private Function<ResolvableTool, URL[]> toolResolver(final Project project) {
        final ConfigurationContainer container = project.getConfigurations();
        return tool -> container.getByName(tool.id()).resolve().stream()
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
                    // Create a version repository. If Gradle is in offline mode, read only from cache
                    final Path cacheDir = this.getParameters().getSharedCache().get().getAsFile().toPath().resolve(Constants.Directories.MANIFESTS);
                    if (this.getParameters().getRefreshDependencies().get()) {
                        this.versions = versions = VersionManifestRepository.direct(this.downloader());
                    } else {
                        this.versions =
                            versions = VersionManifestRepository.caching(this.downloader(), cacheDir, !this.getParameters().getOfflineMode().get());
                    }
                } else {
                    return this.versions;
                }
            }
        }
        return versions;
    }

    @Override
    public void close() {
        MinecraftProviderService.LOGGER.warn("[]: Shutting down MinecraftProviderService");
        this.executor.shutdown();
        boolean success;
        try {
            success = this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
            success = false;
        }

        if (!success) {
            // todo: log warning
            this.executor.shutdownNow();
        }

        final @Nullable ApacheHttpDownloader downloader = this.downloader;
        this.downloader = null;
        if (downloader != null) {
            downloader.close();
        }
    }

}
