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
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.bundler.BundlerMetadata;
import org.spongepowered.gradle.vanilla.internal.model.DownloadClassifier;
import org.spongepowered.gradle.vanilla.internal.model.GroupArtifactVersion;
import org.spongepowered.gradle.vanilla.internal.model.Library;
import org.spongepowered.gradle.vanilla.internal.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.internal.model.rule.RuleContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public enum MinecraftSide {
    CLIENT(DownloadClassifier.CLIENT, DownloadClassifier.CLIENT_MAPPINGS) {
        @Override
        public Set<GroupArtifactVersion> dependencies(
            final VersionDescriptor.Full descriptor,
            final @Nullable BundlerMetadata bundleMetadata
        ) {
            // Client gets all libraries
            return MinecraftSide.manifestLibraries(descriptor, RuleContext.create(), lib -> !lib.isNatives());
        }
    },
    SERVER(DownloadClassifier.SERVER, DownloadClassifier.SERVER_MAPPINGS) {
        private final Set<String> packages;

        {
            final Set<String> allowedPackages = new HashSet<>();
            allowedPackages.add("net/minecraft");
            this.packages = Collections.unmodifiableSet(allowedPackages);
        }

        @Override
        public Set<GroupArtifactVersion> dependencies(
            final VersionDescriptor.Full descriptor,
            final @Nullable BundlerMetadata metadata
        ) {
            if (metadata == null) {
                // Unfortunately Gradle both lets you tweak the metadata of an incoming artifact, and transform the artifact itself, but not both at
                // the same time.
                // Legacy (use a hardcoded list... bleurgh)
                return MinecraftSide.manifestLibraries(
                    descriptor,
                    RuleContext.create(),
                    lib -> !lib.isNatives() && !Constants.CLIENT_ONLY_DEPENDENCY_GROUPS.contains(lib.name().group())
                );
            } else {
                // 21w39+
                return Collections.unmodifiableSet(metadata.libraries().stream()
                    .map(el -> GroupArtifactVersion.parse(el.id()))
                    .collect(Collectors.toSet()));
            }
        }

        @Override
        public void extractJar(
            final Path downloaded, final Path output, final @Nullable BundlerMetadata metadata
        ) throws IOException {
            if (metadata == null) {
                super.extractJar(downloaded, output, metadata);
            } else {
                try (final JarFile jf = new JarFile(downloaded.toFile())) {
                    final JarEntry ent = jf.getJarEntry(metadata.server().path());
                    if (ent == null) {
                        throw new IOException("Could not locate server artifact in " + downloaded + " at " + metadata.server().path());
                    }
                    Files.copy(jf.getInputStream(ent), output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        @Override
        public Set<String> allowedPackages() {
            // Filter this out if there is bundler metadata
            return this.packages;
        }
    };

    private final DownloadClassifier executableArtifact;
    private final DownloadClassifier mappingsArtifact;

    MinecraftSide(final DownloadClassifier executableArtifact, final DownloadClassifier mappingsArtifact) {
        this.executableArtifact = executableArtifact;
        this.mappingsArtifact = mappingsArtifact;
    }

    public final DownloadClassifier executableArtifact() {
        return this.executableArtifact;
    }

    public final DownloadClassifier mappingsArtifact() {
        return this.mappingsArtifact;
    }

    public abstract Set<GroupArtifactVersion> dependencies(
        final VersionDescriptor.Full descriptor,
        final @Nullable BundlerMetadata bundleMetadata
    );

    /**
     * Extract the real jar from any potential bundling.
     *
     * <p>The input file should not be modified</p>
     *
     * @param downloaded the downloaded file from the manifest
     * @param output the output
     * @param metadata bundler metadata to read
     * @throws IOException if an error occurs
     */
    public void extractJar(final Path downloaded, final Path output, final @Nullable BundlerMetadata metadata) throws IOException {
        try {
            Files.createLink(output, downloaded);
        } catch (final IOException ex) {
            Files.copy(downloaded, output, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Get packages that will remain unfiltered in the jar
     *
     * <p>An empty set allows all packages.</p>
     *
     * @return a set of packages to leave unfiltered, or empty to perform no filtering
     */
    public Set<String> allowedPackages() {
        return Collections.emptySet();
    }

    private static Set<GroupArtifactVersion> manifestLibraries(
        final VersionDescriptor.Full manifest,
        final RuleContext rules,
        final Predicate<Library> filter
    ) {
        final Set<GroupArtifactVersion> ret = new HashSet<>();
        for (final Library library : manifest.libraries()) {
            if (library.rules().test(rules) && filter.test(library)) {
                ret.add(library.name());
            }
        }
        return Collections.unmodifiableSet(ret);
    }

}
