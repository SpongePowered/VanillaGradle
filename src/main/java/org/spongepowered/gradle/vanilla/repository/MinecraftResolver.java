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

import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.model.VersionManifestRepository;
import org.spongepowered.gradle.vanilla.network.Downloader;
import org.spongepowered.gradle.vanilla.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.repository.modifier.AssociatedResolutionFlags;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public interface MinecraftResolver {

    /**
     * A version indicator for on-disk storage.
     *
     * <p>When a change in how Minecraft artifacts exists that cannot be
     * detected by existing input detection, or would make data unreadable by
     * older versions of the resolver, this version will be incremented.</p>
     */
    int STORAGE_VERSION = 1;
    /**
     * A version for stored metadata.
     *
     * <p>Whenever the {@link #STORAGE_VERSION} is incremented, this version
     * will be reset to {@code 1}</p>
     */
    int METADATA_VERSION = 1;

    /**
     * Get the version manifest repository managed by this resolver.
     *
     * @return the resolver
     */
    VersionManifestRepository versions();

    // TODO: reproduce 21w14a+ server/client split, where client is stripped of shaded server classes and depends on server artifact?
    // This would kinda blow our MinecraftPlatform model out of the water -- rather than creating a merged jar,
    // we simply strip all entries from the client jar that are also present in the server jar
    // The client artifact would depend on the server artifact

    CompletableFuture<ResolutionResult<MinecraftEnvironment>> provide(final MinecraftPlatform side, final String version);

    CompletableFuture<ResolutionResult<MinecraftEnvironment>> provide(final MinecraftPlatform side, final String version, final Set<ArtifactModifier> modifiers);

    /**
     * Given a standard Minecraft artifact, produce a variant of that artifact.
     *
     * <p>This variant will synchronize back to the current thread before
     * executing the provider function. This allows for operating in
     * concurrency-sensitive environments, such as a Gradle build.</p>
     *
     * @param side the platform to base off of
     * @param version the version to base off of
     * @param id An identifier for this artifact
     * @param flags flags to configure this resolution
     * @param action the action needed to produce a variant, taking the input environment and a target path
     * @return the path of the jar
     */
    ResolutionResult<Path> produceAssociatedArtifactSync(final MinecraftPlatform side, final String version, final Set<ArtifactModifier> modifiers, final String id, final Set<AssociatedResolutionFlags> flags, final BiConsumer<MinecraftEnvironment, Path> action)
        throws Exception;

    interface MinecraftEnvironment {

        /**
         * Get the artifact ID of the resolved Minecraft environment, after
         * modifier information has been encoded.
         *
         * @return the decorated artifact ID
         */
        String decoratedArtifactId();

        /**
         * The output jar of this environment.
         *
         * @return the output jar
         */
        Path jar();

        /**
         * The Mojang-provided metadata for a certain Minecraft environment.
         *
         * @return the version metadata
         */
        VersionDescriptor.Full metadata();

    }

    /**
     * Context available to individual artifact modifier steps.
     */
    interface Context {

        /**
         * A repository exposing the Mojang launcher metadata API.
         *
         * @return the manifest repository
         */
        VersionManifestRepository versions();

        /**
         * A downloader for resources, configured to resolve to the shared
         * cache by default.
         *
         * @return the downloader instance
         */
        Downloader downloader();

        /**
         * An executor for performing asynchronous operations.
         *
         * @return the environment's executor
         */
        Executor executor();

        /**
         * Return a child classloader with a tool and its dependencies on the
         * classpath, as well as the VanillaGradle jar.
         *
         * <p>This is a very fragile arrangement but it allows some dependencies
         * to be overridden at runtime. Classes from Gradle, VanillaGradle's
         * dependencies, and the JDK can be safely shared, but VanillaGradle
         * classes CAN NOT.</p>
         *
         * @param tool the tool to resolve
         * @return a class loader with the tool on the classpath
         */
        Supplier<URLClassLoader> classLoaderWithTool(final ResolvableTool tool);
    }

}
