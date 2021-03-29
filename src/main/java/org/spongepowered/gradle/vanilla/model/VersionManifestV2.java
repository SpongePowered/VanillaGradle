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

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A V2 version manifest.
 *
 * @see VersionManifestRepository to fetch versions
 */
@Value.Immutable
@Gson.TypeAdapters
public interface VersionManifestV2 {

    /**
     * Get the latest version for classifiers.
     *
     * <p>No latest version is provided for certain classifiers such as
     * {@link VersionClassifier#OLD_ALPHA} or {@link VersionClassifier#OLD_BETA}.</p>
     *
     * @return an unmodifiable map of classifier to version ID
     */
    Map<VersionClassifier, String> latest();

    /**
     * Get descriptors for all available versions.
     *
     * @return the version descriptor
     */
    List<VersionDescriptor.Reference> versions();

    /**
     * Attempt to find a version descriptor for a certain version ID.
     *
     * <p>This will only provide information contained in the manifest, without
     * performing network requests.</p>
     *
     * @param id the version ID
     * @return a short descriptor, if any is present
     */
    default Optional<VersionDescriptor.Reference> findDescriptor(final String id) {
        Objects.requireNonNull(id, "id");

        for (final VersionDescriptor.Reference version : this.versions()) {
            if (version.id().equals(id)) {
                return Optional.of(version);
            }
        }

        return Optional.empty();
    }

}
