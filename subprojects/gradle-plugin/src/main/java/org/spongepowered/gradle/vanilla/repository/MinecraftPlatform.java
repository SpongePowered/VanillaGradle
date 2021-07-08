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

import org.spongepowered.gradle.vanilla.resolver.ResolutionResult;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public enum MinecraftPlatform {
    CLIENT(MinecraftSide.CLIENT) {
        @Override
        CompletableFuture<ResolutionResult<MinecraftResolver.MinecraftEnvironment>> resolveMinecraft(
            final MinecraftResolverImpl resolver, final String version, final Path outputJar
        ) {
            return resolver.provide(MinecraftPlatform.CLIENT, MinecraftSide.CLIENT, version, outputJar);
        }
    },
    SERVER(MinecraftSide.SERVER) {
        @Override
        CompletableFuture<ResolutionResult<MinecraftResolver.MinecraftEnvironment>> resolveMinecraft(
            final MinecraftResolverImpl resolver, final String version, final Path outputJar
        ) {
            return resolver.provide(MinecraftPlatform.SERVER, MinecraftSide.SERVER, version, outputJar);
        }
    },
    JOINED(MinecraftSide.CLIENT, MinecraftSide.SERVER) {
        @Override
        CompletableFuture<ResolutionResult<MinecraftResolver.MinecraftEnvironment>> resolveMinecraft(
            final MinecraftResolverImpl resolver, final String version, final Path outputJar
        ) {
            final CompletableFuture<ResolutionResult<MinecraftResolver.MinecraftEnvironment>> clientFuture = resolver.provide(MinecraftPlatform.CLIENT, version);
            final CompletableFuture<ResolutionResult<MinecraftResolver.MinecraftEnvironment>> serverFuture = resolver.provide(MinecraftPlatform.SERVER, version);
            return resolver.provideJoined(clientFuture, serverFuture, version, outputJar);
        }
    };

    /**
     * The common group for Minecraft artifacts.
     */
    public static final String GROUP = "net.minecraft";

    private static final Map<String, MinecraftPlatform> BY_ID;

    private final String artifactId;
    private final Set<MinecraftSide> activeSides;

    MinecraftPlatform(final MinecraftSide... sides) {
        this.artifactId = this.name().toLowerCase(Locale.ROOT);
        this.activeSides = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(sides)));
    }

    public static Collection<MinecraftPlatform> all() {
        return MinecraftPlatform.BY_ID.values();
    }

    public static Optional<MinecraftPlatform> byId(final String name) {
        return Optional.ofNullable(MinecraftPlatform.BY_ID.get(name));
    }

    /**
     * Get the full module name in {@code <group>:<artifact>} format.
     *
     * @return the full module name
     */
    public String moduleName() {
        return MinecraftPlatform.GROUP + ':' + this.artifactId;
    }

    public String artifactId() {
        return this.artifactId;
    }

    public boolean includes(final MinecraftSide platform) {
        return this.activeSides.contains(platform);
    }

    public Set<MinecraftSide> activeSides() {
        return this.activeSides;
    }

    abstract CompletableFuture<ResolutionResult<MinecraftResolver.MinecraftEnvironment>> resolveMinecraft(final MinecraftResolverImpl resolver, final String version, final Path outputJar);

    static {
        final Map<String, MinecraftPlatform> all = new HashMap<>();
        for (final MinecraftPlatform platform : MinecraftPlatform.values()) {
            all.put(platform.artifactId, platform);
        }
        BY_ID = Collections.unmodifiableMap(all);
    }
}
