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
package org.spongepowered.gradle.vanilla.internal.bundler;

import org.cadixdev.atlas.jar.JarFile;
import org.cadixdev.atlas.jar.JarPath;
import org.cadixdev.bombe.jar.AbstractJarEntry;
import org.cadixdev.bombe.jar.JarManifestEntry;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value.Immutable
public abstract class BundlerMetadata {

    private static final JarPath MANIFEST = new JarPath("META-INF/MANIFEST.MF");

    private static final JarPath LIBRARIES_LIST = new JarPath("META-INF/libraries.list");

    private static final JarPath VERSIONS_LIST = new JarPath("META-INF/versions.list");

    private static final JarPath MAIN_CLASS = new JarPath("META-INF/main-class");

    /**
     * Attempt to read bundler metadata from a jar.
     *
     * <p>If the jar is not a Minecraft bundler jar, an empty {@link Optional} will
     * be returned.</p>
     *
     * @param jar the jar to read
     * @return parsed metadata
     * @throws IOException if an error occurs while trying to read from the jar
     */
    public static Optional<BundlerMetadata> read(final Path jar) throws IOException {
        try (final JarFile file = new JarFile(jar)) {
            final Manifest manifest = ((JarManifestEntry) file.get(BundlerMetadata.MANIFEST)).getManifest();
            final @Nullable String formatVersion = manifest.getMainAttributes().getValue(FormatVersion.MANIFEST_ATTRIBUTE);
            if (formatVersion == null) {
                return Optional.empty();
            }

            final FormatVersion parsed = FormatVersion.parse(formatVersion);

            // load information:
            // server jar
            final BundleElement serverJar;
            try (final Stream<BundleElement> stream = BundlerMetadata.readIndex(file, "versions")) {
                serverJar = stream.findFirst()
                    .orElse(null);
            }

            if (serverJar == null) {
                throw new IllegalArgumentException("Missing server jar from versions list");
            }

            // libraries list
            final Set<BundleElement> libraries;
            try (final Stream<BundleElement> elements = BundlerMetadata.readIndex(file, "libraries")) {
                libraries = Collections.unmodifiableSet(elements.collect(Collectors.toSet()));
            }

            // main class
            final AbstractJarEntry mainClassEntry = file.getClass(BundlerMetadata.MAIN_CLASS);
            if (mainClassEntry == null) {
                throw new IllegalArgumentException("Missing main class entry in bundle");
            }

            final String mainClass = new String(mainClassEntry.getContents(), StandardCharsets.UTF_8).trim();

            return Optional.of(BundlerMetadata.of(parsed, libraries, serverJar, mainClass));
        }
    }

    public static BundlerMetadata of(final FormatVersion version, final Set<BundleElement> libraries, final BundleElement server, final @Nullable String mainClass) {
        return new BundlerMetadataImpl(version, libraries, server, mainClass);
    }

    private static Stream<BundleElement> readIndex(final JarFile jar, final String index) throws IOException {
        final JarPath path = new JarPath("META-INF/" + index + ".list");
        final @Nullable AbstractJarEntry entry = jar.get(path);
        if (entry == null) {
            return Stream.empty();
        }

        final InputStream is = new ByteArrayInputStream(entry.getContents());
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        return reader.lines()
            .map(x -> x.split("\t"))
            .map(line -> BundleElement.of(line[0], line[1], "META-INF/" + index + "/" + line[2]))
            .onClose(() -> {
                try {
                    reader.close();
                } catch (final IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
    }

    /**
     * The bundler format used by this jar.
     *
     * <p>While VanillaGradle only knows about versions that existed at the time
     * of its release, we will attempt to read future versions as well.</p>
     *
     * @return the format version
     */
    @Value.Parameter
    public abstract FormatVersion version();

    /**
     * Libraries packed in the jar as dependencies for the server.
     *
     * @return the library elements
     */
    @Value.Parameter
    public abstract Set<BundleElement> libraries();

    /**
     * Get a bundle element describing the server itself.
     *
     * @return an index entry describing the server
     */
    @Value.Parameter
    public abstract BundleElement server();

    /**
     * The main class to execute.
     *
     * @return the main class
     */
    @Value.Parameter
    public abstract @Nullable String mainClass();

}
