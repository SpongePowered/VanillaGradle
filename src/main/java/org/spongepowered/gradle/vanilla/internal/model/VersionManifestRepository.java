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
package org.spongepowered.gradle.vanilla.internal.model;

import org.spongepowered.gradle.vanilla.internal.network.network.Downloader;
import org.spongepowered.gradle.vanilla.repository.ResolutionResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A repository resolving version manifests.
 */
public interface VersionManifestRepository {

    /**
     * Create a repository that will fetch version manifests from the API endpoint.
     *
     * <p>This repository will cache information based on the parameters of the
     * provided downloader.</p>
     *
     * @return a new downloader-based repository
     * @param downloader the downloader to use to fetch remote resources
     */
    static VersionManifestRepository fromDownloader(final Downloader downloader) {
        return new DownloaderBasedVersionManifestRepository(downloader);
    }

    // TODO: Create a repository that doesn not depend on Downloader? for standalone release

    /**
     * Fetch the backing manifest.
     *
     * @return the backing manifest
     */
    CompletableFuture<VersionManifestV2> manifest();

    /**
     * Get all available versions.
     *
     * @return the collection of available versions
     */
    CompletableFuture<List<? extends VersionDescriptor>> availableVersions();

    /**
     * Get the identifier for the latest version of a particular type.
     *
     * @param classifier the version classifier to query the latest version of.
     * @return a version id if any is present for the classifier
     */
    CompletableFuture<Optional<String>> latestVersion(final VersionClassifier classifier);

    /**
     * Query a full version for a specific id.
     *
     * @param versionId the ID of the version to query
     * @return a version descriptor
     */
    CompletableFuture<ResolutionResult<VersionDescriptor.Full>> fullVersion(final String versionId);

    /**
     * Inject a pre-existing local version descriptor.
     *
     * <p>This can be used to target out-of-band releases not available via the
     * normal metadata API.</p>
     *
     * <p>Injected versions will not be tracked by any caching storage that a
     * repository may implement, but must be stored in-memory. To continue
     * making an injected version available, it must be provided on
     * every run.</p>
     *
     * @param localDescriptor the local file containing the JSON representation
     *     of a {@link VersionDescriptor.Full}
     * @throws IOException any errors that occur loading the descriptor.
     * @return the {@link VersionDescriptor#id()} of the loaded version
     */
    String inject(final Path localDescriptor) throws IOException;

    /**
     * Inject a pre-existing local version descriptor.
     *
     * @param localDescriptor the local file containing the JSON representation
     *     of a {@link VersionDescriptor.Full}
     * @throws IOException if any errors occur while attempting to load the
     *     descriptor.
     * @return the {@link VersionDescriptor#id()} of the loaded version
     * @see #inject(Path) for more details
     */
    default String inject(final File localDescriptor) throws IOException {
        return this.inject(localDescriptor.toPath());
    }

    /**
     * Promote a reference to a full version description.
     *
     * <p>If the passed descriptor is a reference, a network lookup may occur to
     * resolve a full descriptor.</p>
     *
     * <p>Behaviour is undefined when attempting to fetch a result for a
     * {@link VersionDescriptor} not obtained from this repository.</p>
     *
     * @param unknown a version descriptor of unknown type
     * @return a full version
     */
    default CompletableFuture<VersionDescriptor.Full> promote(final VersionDescriptor unknown) {
        if (unknown instanceof VersionDescriptor.Full) {
            return CompletableFuture.completedFuture((VersionDescriptor.Full) unknown);
        } else {
            return this.fullVersion(unknown.id())
                .thenApply(result -> result
                    .orElseThrow(() -> new IllegalArgumentException("Version descriptor " + unknown.id() + " was not found in this repository!")));
        }
    }

}
