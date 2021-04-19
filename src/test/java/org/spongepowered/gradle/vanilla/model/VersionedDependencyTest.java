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
import org.opentest4j.AssertionFailedError;
import org.spongepowered.gradle.vanilla.model.rule.OperatingSystemRule;
import org.spongepowered.gradle.vanilla.model.rule.RuleContext;
import org.spongepowered.gradle.vanilla.network.UrlConnectionDownloader;
import org.spongepowered.gradle.vanilla.util.GsonUtils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

class VersionedDependencyTest {

    @Test
    @Disabled("Makes network requests")
    void dependenciesFromManifest() throws IOException, ExecutionException, InterruptedException {
        final VersionManifestRepository repo = VersionManifestRepository.direct(new UrlConnectionDownloader(Paths.get("."), GsonUtils.GSON,
            ForkJoinPool.commonPool()
        ));

        final String latestName = repo.latestVersion(VersionClassifier.RELEASE).get().orElseThrow(() -> new IllegalStateException("No latest release!"));
        final VersionDescriptor.Full actual = repo.fullVersion(latestName).get();

        final RuleContext context = RuleContext.create();
        OperatingSystemRule.setOsName(context, "osx");
        for (final Library library : actual.libraries()) {
            if (library.rules().test(context)) {
                System.out.println(library.name());
            }
        }
    }

    @Test
    @Disabled("Fairly resource-intensive, takes a while to download all uncached")
    void testLoadAllManifests() throws IOException, ExecutionException, InterruptedException {
        final VersionManifestRepository repo = VersionManifestRepository.direct(new UrlConnectionDownloader(Paths.get("."), GsonUtils.GSON,
            ForkJoinPool.commonPool()
        ));
        // final VersionManifestRepository repo = VersionManifestRepository.caching(Paths.get("test-cache"), true);

        for (final VersionDescriptor version : repo.availableVersions().get()) {
            System.out.println(version);
            repo.fullVersion(version.id()).get();
        }
    }
}
