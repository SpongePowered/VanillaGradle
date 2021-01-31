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

import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.util.GsonUtils;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class VersionManifestV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<VersionClassifier, String> latest;
    private List<VersionDescriptor> versions;

    public Map<VersionClassifier, String> latest() {
        return this.latest;
    }

    public List<VersionDescriptor> versions() {
        return this.versions;
    }

    public Optional<VersionDescriptor> findDescriptor(final String id) {
        Objects.requireNonNull(id, "id");

        for (final VersionDescriptor version : this.versions) {
            if (version.id().equals(id)) {
                return Optional.of(version);
            }
        }

        return Optional.empty();
    }

    public static VersionManifestV2 load() throws IOException {
        final URL url = new URL(Constants.Manifests.API_V2_ENDPOINT);
        return GsonUtils.parseFromJson(url, VersionManifestV2.class);
    }
}
