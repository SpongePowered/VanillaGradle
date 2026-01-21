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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.spongepowered.gradle.vanilla.internal.model.Library;
import org.spongepowered.gradle.vanilla.internal.model.VersionClassifier;
import org.spongepowered.gradle.vanilla.internal.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.internal.model.VersionManifestRepository;
import org.spongepowered.gradle.vanilla.internal.model.rule.OperatingSystemRule;
import org.spongepowered.gradle.vanilla.internal.model.rule.RuleContext;
import org.spongepowered.gradle.vanilla.resolver.Downloader;
import org.spongepowered.gradle.vanilla.resolver.ResolutionResult;
import org.spongepowered.gradle.vanilla.resolver.jdk.JdkHttpClientDownloader;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

class VersionManifestRepositoryTest {

    @Test
    @Disabled("Makes network requests")
    void dependenciesFromManifest() throws IOException, ExecutionException, InterruptedException {
        try (final Downloader downloader = JdkHttpClientDownloader.uncached(ForkJoinPool.commonPool())) {
            final VersionManifestRepository repo = VersionManifestRepository.fromDownloader(downloader);

            final String latestName =
                repo.latestVersion(VersionClassifier.RELEASE).get().orElseThrow(() -> new IllegalStateException("No latest release!"));
            final VersionDescriptor.Full actual =
                repo.fullVersion(latestName).get().orElseThrow(() -> new IllegalStateException("No version descriptor for latest release!"));

            final RuleContext context = RuleContext.create();
            OperatingSystemRule.setOsName(context, "osx");
            for (final Library library : actual.libraries()) {
                if (library.rules().test(context)) {
                    System.out.println(library.name());
                }
            }
        }
    }

    @Test
    @Disabled("Fairly resource-intensive, takes a while to download all uncached (~1 minute)")
    void testLoadAllManifests() throws IOException, ExecutionException, InterruptedException {
        try (final Downloader downloader = new JdkHttpClientDownloader(ForkJoinPool.commonPool(), Paths.get("test-cache"), Downloader.ResolveMode.LOCAL_THEN_REMOTE)) {
            final VersionManifestRepository repo = VersionManifestRepository.fromDownloader(downloader);

            final Set<CompletableFuture<ResolutionResult<VersionDescriptor.Full>>> descriptors = new HashSet<>();
            for (final VersionDescriptor version : repo.availableVersions().get()) {
                System.out.println(version);
                descriptors.add(repo.fullVersion(version.id()).whenComplete((result, err) -> {
                    if (err != null) {
                        System.out.println("Failed to download " + version.id());
                        err.printStackTrace();
                    } else if (result.isPresent()) {
                        System.out.println("Finished " + result.get().id() + " (up-to-date: " + result.upToDate() + ")");
                    } else {
                        System.out.println("Result not found for " + version.id());
                    }
                }));
            }

            System.out.println("Awaiting");
            CompletableFuture.allOf(descriptors.toArray(new CompletableFuture<?>[0])).get();
            System.out.println("Done");
        }
    }
}
