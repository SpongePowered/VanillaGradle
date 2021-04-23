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
package org.spongepowered.gradle.vanilla.repository.modifier;

import org.cadixdev.atlas.AtlasTransformerContext;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.spongepowered.gradle.vanilla.network.HashAlgorithm;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.ResolvableTool;
import org.spongepowered.gradle.vanilla.util.AsyncUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class AccessWidenerModifier implements ArtifactModifier {

    private static final String KEY = "aw";

    private final Set<Path> wideners;
    private volatile @MonotonicNonNull String stateKey;

    public AccessWidenerModifier(final Set<File> wideners) {
        this.wideners = wideners.stream()
            .map(File::toPath)
            .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
    }

    @Override
    public String key() {
        return AccessWidenerModifier.KEY;
    }

    @Override
    public String stateKey() {
        if (this.stateKey == null) {
            final MessageDigest digest = HashAlgorithm.SHA1.digest();
            for (final Path widenerFile : this.wideners) {
                try (final InputStream is = Files.newInputStream(widenerFile)) {
                    final byte[] buf = new byte[4096];
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        digest.update(buf, 0, read);
                    }
                } catch (final IOException ex) {
                    // ignore, will show up when we try to actually access-widen the jar
                }
            }
            return this.stateKey = HashAlgorithm.toHexString(digest.digest());
        }
        return this.stateKey;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<AtlasPopulator> providePopulator(
        final MinecraftResolver.Context context
    ) {
        final Supplier<URLClassLoader> loaderProvider = context.classLoaderWithTool(ResolvableTool.ACCESS_WIDENER);
        return AsyncUtils.failableFuture(() -> new AtlasPopulator() {
            private final URLClassLoader loader = loaderProvider.get();
            private final Function<Set<Path>, JarEntryTransformer> accessWidenerLoader = (Function<Set<Path>, JarEntryTransformer>) Class.forName(
                "org.spongepowered.gradle.vanilla.worker.AccessWidenerTransformerProvider",
                true,
                this.loader
            )
                .getConstructor()
                .newInstance();

            @Override
            public JarEntryTransformer provide(final AtlasTransformerContext context) {
                return this.accessWidenerLoader.apply(AccessWidenerModifier.this.wideners);
            }

            @Override
            public void close() throws IOException {
                this.loader.close();
            }
        }, context.executor());
    }

    @Override
    public boolean requiresLocalStorage() {
        return true;
    }
}
