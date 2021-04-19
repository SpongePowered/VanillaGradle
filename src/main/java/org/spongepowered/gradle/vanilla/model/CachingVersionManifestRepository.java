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

import com.google.gson.JsonParseException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.network.Downloader;
import org.spongepowered.gradle.vanilla.network.HashAlgorithm;
import org.spongepowered.gradle.vanilla.util.CopyingInputStream;
import org.spongepowered.gradle.vanilla.util.GsonUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/*final class CachingVersionManifestRepository implements VersionManifestRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CachingVersionManifestRepository.class);
    private static final String VERSION_MANIFEST_FILE = "version_manifest_v2.json";
    private static final String VERSIONS_DIRECTORY = "versions";
    private static final String ATTRIBUTE_CREATION_TIME = "basic:creationTime";

    private final Downloader downloader;
    private final Path cacheDir;
    private final boolean queryRemote;
    private volatile @Nullable VersionManifestV2 manifest;
    private final Map<String, VersionDescriptor.Full> loadedVersions = new ConcurrentHashMap<>();
    private final Set<VersionDescriptor.Full> injectedVersions = new HashSet<>(); // all injected versions are also in loadedVersions

    public CachingVersionManifestRepository(final Downloader downloader, final Path cacheDir, final boolean queryRemote) {
        this.cacheDir = cacheDir;
        this.queryRemote = queryRemote;
    }

    private <V> @Nullable V fetchIfPresent(final Path expected, final @Nullable String expectedSha1, final Class<V> type) throws IOException {
        final BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(expected, BasicFileAttributes.class);
        } catch (final IOException ex) {
            return null; // if does not exist
        }

        if (this.queryRemote
            && (System.currentTimeMillis() - attributes.lastModifiedTime().toMillis()) > Constants.Manifests.CACHE_TIMEOUT_SECONDS * 1000) {
            // too old
            return null;
        }

        if (expectedSha1 != null) {
            if (!HashAlgorithm.SHA1.validate(expectedSha1, expected)) {
                CachingVersionManifestRepository.LOGGER.warn("Failed to validate hash for file {} when loading from cache", expected);
                return null;
            }
        }
        try {
            return GsonUtils.parseFromJson(expected, type);
        } catch (final IOException | JsonParseException ex) {
            CachingVersionManifestRepository.LOGGER.warn("Failed to load data for file {} from cache: ", expected, ex);
            return null;
        }
    }

    private <V> V updateCache(final Path expected, final URL source, final Class<V> type) throws IOException {
        Files.createDirectories(expected.getParent());
        final V value;
        try (final OutputStream os = Files.newOutputStream(expected);
             final BufferedReader reader = new BufferedReader(new InputStreamReader(new CopyingInputStream(source.openStream(), os), StandardCharsets.UTF_8))) {
            value = GsonUtils.GSON.fromJson(reader, type);
        }
        Files.setAttribute(expected, CachingVersionManifestRepository.ATTRIBUTE_CREATION_TIME, FileTime.fromMillis(System.currentTimeMillis()));
        return value;
    }

    @Override
    public CompletableFuture<VersionManifestV2> manifest() {
        @Nullable VersionManifestV2 manifest = this.manifest;
        if (manifest == null) {
            this.manifest = manifest = this.fetchManifest(true);
        }

        if (manifest != null) {
            return manifest;
        }

        throw new IOException("No version manifest is available!");
    }

    private @Nullable VersionManifestV2 fetchManifest(final boolean useCache) throws IOException {
        final Path versionManifest = this.cacheDir.resolve(CachingVersionManifestRepository.VERSION_MANIFEST_FILE);
        @Nullable VersionManifestV2 manifest;
        if (useCache) {
            manifest = this.fetchIfPresent(versionManifest, null, VersionManifestV2.class);
        } else {
            manifest = null;
        }

        if (manifest == null && this.queryRemote) {
            manifest = this.updateCache(versionManifest, new URL(Constants.Manifests.API_V2_ENDPOINT), VersionManifestV2.class);
        }

        return manifest;
    }

    @Override
    public CompletableFuture<List<? extends VersionDescriptor>> availableVersions() {
        try {
            if (this.injectedVersions.isEmpty()) {
                return this.manifest().versions();
            } else {
                final List<VersionDescriptor> descriptors = new ArrayList<>(this.manifest().versions());
                descriptors.addAll(this.injectedVersions);
                return descriptors;
            }
        } catch (final IOException ex) {
            CachingVersionManifestRepository.LOGGER.warn("Failed to retrieve available Minecraft versions", ex);
            return Collections.emptyList();
        }
    }

    @Override
    public CompletableFuture<Optional<String>> latestVersion(final VersionClassifier classifier) {
        try {
            return Optional.ofNullable(this.manifest().latest().get(classifier));
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<VersionDescriptor.Full> fullVersion(final String versionId) {
        // Cache version if it exists
        VersionDescriptor.Full cachedVersion = this.loadedVersions.get(versionId);
        if (cachedVersion != null) {
            return Optional.of(cachedVersion);
        }

        final Path cacheLocation = this.cacheDir.resolve(CachingVersionManifestRepository.VERSIONS_DIRECTORY).resolve(versionId + ".json");
        // Otherwise, find a descriptor in cached manifest
        VersionDescriptor.@Nullable Reference descriptor = this.manifest().findDescriptor(versionId).orElse(null);
        if (descriptor == null) {
            // If we can't find a descriptor in the cached manifest, clear cache and try again
            if (this.queryRemote) {
                this.manifest = this.fetchManifest(false);
                if (this.manifest == null) {
                    return Optional.empty();
                }

                descriptor = this.manifest.findDescriptor(versionId).orElse(null);
                if (descriptor == null) {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        }

        cachedVersion = this.fetchIfPresent(cacheLocation, descriptor.sha1(), VersionDescriptor.Full.class);
        if (cachedVersion == null && this.queryRemote) {
            cachedVersion = this.updateCache(cacheLocation, descriptor.url(), VersionDescriptor.Full.class);
        }

        if (cachedVersion == null) {
            return Optional.empty();
        }

        // Then store back in cache
        this.loadedVersions.put(versionId, cachedVersion);
        return Optional.of(cachedVersion);
    }

    @Override
    public String inject(final Path localDescriptor) throws IOException {
        Objects.requireNonNull(localDescriptor, "localDescriptor");
        final VersionDescriptor.Full version = GsonUtils.parseFromJson(localDescriptor, VersionDescriptor.Full.class);
        this.loadedVersions.put(version.id(), version);
        this.injectedVersions.add(version);
        return version.id();
    }
}*/
