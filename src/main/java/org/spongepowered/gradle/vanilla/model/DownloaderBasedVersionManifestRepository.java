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
package org.spongepowered.gradle.vanilla.model;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.network.Downloader;
import org.spongepowered.gradle.vanilla.network.HashAlgorithm;
import org.spongepowered.gradle.vanilla.repository.ResolutionResult;
import org.spongepowered.gradle.vanilla.util.GsonUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class DownloaderBasedVersionManifestRepository implements VersionManifestRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloaderBasedVersionManifestRepository.class);

    private final Downloader downloader;
    private volatile @Nullable CompletableFuture<VersionManifestV2> manifest;
    private final Map<String, VersionDescriptor.Full> injectedVersions = new ConcurrentHashMap<>(); // Add-only
    private final Map<String, CompletableFuture<ResolutionResult<VersionDescriptor.Full>>> resolvedVersions = new ConcurrentHashMap<>();

    DownloaderBasedVersionManifestRepository(final Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public CompletableFuture<VersionManifestV2> manifest() {
        @Nullable CompletableFuture<VersionManifestV2> manifest = this.manifest;
        if (manifest == null) {
            final URL url;
            try {
                url = new URL(Constants.Manifests.API_V2_ENDPOINT);
            } catch (final MalformedURLException ex) {
                throw new IllegalStateException("Constant API URL failed to parse", ex);
            }
            this.manifest = manifest = this.downloader.readString(url, "manifest.json") // it's fine if we download multiple times, the downloader ensures we do it safely
                .thenApply(res -> GsonUtils.GSON.fromJson(res.get(), VersionManifestV2.class)); // ensure it's present
        }
        return manifest;
    }

    @Override
    public CompletableFuture<List<? extends VersionDescriptor>> availableVersions() {
        return this.manifest().thenApply(manifest -> {
            if (this.injectedVersions.isEmpty()) {
                return manifest.versions();
            } else {
                final List<VersionDescriptor> versions = new ArrayList<>(manifest.versions());
                versions.addAll(this.injectedVersions.values());
                return versions;
            }
        }).exceptionally(ex -> {
            DownloaderBasedVersionManifestRepository.LOGGER.error("Failed to query Minecraft version manifest: ", ex);
            return Collections.emptyList();
        });
    }

    @Override
    public CompletableFuture<Optional<String>> latestVersion(final VersionClassifier classifier) {
        return this.manifest()
            .thenApply(manifest -> Optional.ofNullable(manifest.latest().get(classifier)))
            .exceptionally(ex -> {
                DownloaderBasedVersionManifestRepository.LOGGER.error("Failed to query latest version: ", ex);
                return Optional.empty();
            });
    }

    @Override
    public CompletableFuture<ResolutionResult<VersionDescriptor.Full>> fullVersion(final String versionId) {
        return this.resolvedVersions.computeIfAbsent(versionId, version -> this.manifest().thenCompose(manifest -> {
            final VersionDescriptor.@Nullable Reference option = manifest.findDescriptor(version).orElse(null);
            if (option == null) {
                return CompletableFuture.completedFuture(ResolutionResult.notFound());
            }
            return this.downloader.readStringAndValidate(
                option.url(),
                "versions/" + option.id() + ".json",
                HashAlgorithm.SHA1,
                option.sha1()
            ).thenApply(res -> res.mapIfPresent((upToDate, content) -> GsonUtils.GSON.fromJson(content, VersionDescriptor.Full.class)));
        }));
    }

    @Override
    public String inject(final Path localDescriptor) throws IOException {
        // TODO: Maybe only allow this before anything has been resolved
        // That restriction would probably cause issues in multi-project builds, if more than one registers custom versions
        // ... we do have a settings plugin for that though...
        final VersionDescriptor.Full descriptor = GsonUtils.parseFromJson(localDescriptor, VersionDescriptor.Full.class);
        this.resolvedVersions.put(descriptor.id(), CompletableFuture.completedFuture(ResolutionResult.result(descriptor, false))); // todo: does this make sense?... we have no real way to track whether these descriptors changed
        this.injectedVersions.put(descriptor.id(), descriptor);
        return descriptor.id();
    }

}
