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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

@Value.Enclosing
@Gson.TypeAdapters
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

    OptionalInt complianceLevel();

    /**
     * A reference to a full version.
     */
    @Value.Immutable
    interface Reference extends VersionDescriptor {

        /**
         * A url where the {@link Full} version descriptor can be found.
         *
         * @return a URL for the full descriptor
         */
        URL url();

        String sha1();

    }

    /**
     * The full descriptor for a <em>Minecraft: Java Edition</em> version.
     *
     * Most fields are common across versions.
     */
    @Value.Immutable
    interface Full extends VersionDescriptor {

        Optional<Arguments> arguments();

        @Gson.Named("minecraftArguments")
        Optional<String> legacyArguments();

        @Value.Check
        default void checkArguments() {
            if (!this.arguments().isPresent() && !this.legacyArguments().isPresent()) {
                throw new IllegalStateException("Either legacy or modern arguments must be set");
            } else if (this.arguments().isPresent() && this.legacyArguments().isPresent()) {
                throw new IllegalStateException("Only one of legacy or modern arguments can be set");
            }
        }

        AssetIndexReference assetIndex();

        String assets();

        Map<DownloadClassifier, Download> downloads();

        List<Library> libraries();

        /**
         * Checks if the natives should be on the classpath.
         * <p></p>
         * <b>Note: </b> Since <b>1.19-pre1</b>, the natives are on the classpath.
         *
         * @return {@code true} if hte natives should be on the classpath, {@code false} otherwise.
         */
        default boolean nativesOnClasspath() {
            return this.libraries().stream().allMatch(library -> library.natives().isEmpty());
        }

        Optional<JsonObject> logging();

        String mainClass();

        int minimumLauncherVersion();

        /**
         * Get the Java version this Minecraft version is designed for.
         *
         * <p>Older version manifests may not include this property. For those,
         * Java 8 should be assumed.</p>
         *
         * @return the java version to use
         */
        @Nullable JavaRuntimeVersion javaVersion();

        default Optional<Download> download(final DownloadClassifier classifier) {
            Objects.requireNonNull(classifier, "classifier");

            return Optional.ofNullable(this.downloads().get(classifier));
        }

        default Download requireDownload(final DownloadClassifier classifier) {
            return this.download(classifier)
                    .orElseThrow(() -> new RuntimeException("No " + classifier + " download information was within the manifest!"));
        }

    }
}
