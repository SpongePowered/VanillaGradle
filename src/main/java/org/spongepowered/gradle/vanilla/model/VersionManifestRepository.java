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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A repository resolving version manifests.
 */
public interface VersionManifestRepository {

    /**
     * Create a repository that will fetch version manifests from the API endpoint.
     *
     * <p>This repository will perform limited in-memory caching of
     * version data.</p>
     *
     * @return a new direct repository
     */
    static VersionManifestRepository direct() {
        return new DirectVersionManifestRepository();
    }

    /**
     * Get a manifest repository that caches responses.
     *
     * <p>If {@code queryRemote} is {@code false}, only existing cached data will be
     * fetched. Expiration checks will also be ignored in this case.</p>
     *
     * @param cacheDir the directory to store cached responses in
     * @param queryRemote whether to query the remote repository
     * @return the repository
     */
    static VersionManifestRepository caching(final Path cacheDir, final boolean queryRemote) {
        if (Files.isDirectory(cacheDir) || !Files.exists(cacheDir)) {
            return new CachingVersionManifestRepository(cacheDir, queryRemote);
        } else {
            throw new IllegalArgumentException("Provided cache directory " + cacheDir + " is already a file, when a directory was expected!");
        }
    }

    /**
     * Fetch the backing manifest.
     *
     * @return the backing manifest
     */
    VersionManifestV2 manifest() throws IOException;

    /**
     * Get all available versions.
     *
     * @return the collection of available versions
     */
    List<? extends VersionDescriptor> availableVersions();

    /**
     * Get the identifier for the latest version of a particular type.
     *
     * @param classifier the version classifier to query the latest version of.
     * @return a version id if any is present for the classifier
     */
    Optional<String> latestVersion(final VersionClassifier classifier);

    /**
     * Query a full version for a specific id.
     *
     * <p>This may block.</p>
     *
     * @param versionId the ID of the version to query
     * @return a version descriptor
     */
    Optional<VersionDescriptor.Full> fullVersion(final String versionId) throws IOException;

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
    default VersionDescriptor.Full promote(final VersionDescriptor unknown) throws IOException {
        if (unknown instanceof VersionDescriptor.Full) {
            return (VersionDescriptor.Full) unknown;
        } else {
            return this.fullVersion(unknown.id())
                .orElseThrow(() -> new IllegalArgumentException("Version descriptor " + unknown.id() + " was not found in this repository!"));
        }
    }

}
