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
import org.spongepowered.gradle.vanilla.network.Downloader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public abstract class MinecraftProviderService implements BuildService<MinecraftProviderService.Parameters>, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftProviderService.class);

    private volatile @Nullable Downloader downloader;
    private volatile @Nullable MinecraftResolverImpl resolver;
    private volatile @Nullable VersionManifestRepository versions;
    private final ExecutorService executor;

    public interface Parameters extends BuildServiceParameters {
        DirectoryProperty getSharedCache(); // global cache
        DirectoryProperty getRootProjectCache(); // root project cache, used for any transformed artifacts that are reliant on project data
        Property<Boolean> getOfflineMode(); // gradle -o offline mode parameter, only resolve from local cache
        Property<Boolean> getRefreshDependencies(); // gradle --refresh-dependencies start parameter, ignore existing data in local cache
    }

    public MinecraftProviderService() {
        // aaa
        /* this.executor = new ThreadPoolExecutor(maxProcessors / 4, maxProcessors,
            30, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>()); */
        this.executor = Executors.newWorkStealingPool(); // todo: use from above
        MinecraftProviderService.LOGGER.info("Creating minecraft provider service");
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

    public MinecraftResolver resolver(final Project project) {
        @Nullable MinecraftResolverImpl resolver = this.resolver;
        if (resolver == null) {
            synchronized (this) {
                if (this.resolver == null) {
                    this.resolver = resolver = new MinecraftResolverImpl(
                        this.versions(),
                        this.downloader().withBaseDir(this.downloader().baseDir().resolve(Constants.Directories.JARS)),
                        this.executor
                    );
                } else {
                    return this.resolver.setResolver(this.toolResolver(project));
                }
            }
        }
        return resolver.setResolver(this.toolResolver(project));
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

}
