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

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface VersionDescriptor {

    /**
     * A unique identifier for this version.
     *
     * <p>Versions may be updated over time, though there will be only one
     * descriptor for each key in the manifest at any given time.</p>
     *
     * @return the version ID
     */
    String id();

    VersionClassifier type();

    ZonedDateTime time();

    ZonedDateTime releaseTime();

    int complianceLevel();

    /**
     * A reference to a full version.
     *
     * @param url A url where the {@link Full} version descriptor can be found.
     */
    record Reference(String id, VersionClassifier type, ZonedDateTime time, ZonedDateTime releaseTime, int complianceLevel,
                     URL url, String sha1) implements VersionDescriptor {
    }

    /**
     * The full descriptor for a <em>Minecraft: Java Edition</em> version.
     * Most fields are common across versions.
     *
     * @param minecraftArguments Legacy arguments.
     * @param javaVersion The Java version this Minecraft version is designed for.
     */
    record Full(String id, VersionClassifier type, ZonedDateTime time, ZonedDateTime releaseTime, int complianceLevel,
                @Nullable Arguments arguments, @Nullable String minecraftArguments, AssetIndexReference assetIndex, String assets,
                Map<DownloadClassifier, Download> downloads, List<Library> libraries, @Nullable JsonObject logging, String mainClass,
                int minimumLauncherVersion, @Nullable JavaRuntimeVersion javaVersion) implements VersionDescriptor {

        public Full {
            if (arguments == null && minecraftArguments == null) {
                throw new IllegalStateException("Either legacy or modern arguments must be set");
            } else if (arguments != null && minecraftArguments != null) {
                throw new IllegalStateException("Only one of legacy or modern arguments can be set");
            }
        }

        public Optional<Download> download(final DownloadClassifier classifier) {
            Objects.requireNonNull(classifier, "classifier");
            return Optional.ofNullable(this.downloads().get(classifier));
        }

        public Download requireDownload(final DownloadClassifier classifier) {
            return this.download(classifier).orElseThrow(() -> new RuntimeException("No " + classifier + " download information was within the manifest!"));
        }

    }
}
