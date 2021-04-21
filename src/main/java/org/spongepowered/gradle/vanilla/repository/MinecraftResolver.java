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

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface MinecraftResolver {

    VersionManifestRepository versions();

    // TODO: reproduce 21w14a+ server/client split, where client is stripped of shaded server classes and depends on server artifact?
    // This would kinda blow our MinecraftPlatform model out of the water -- rather than creating a merged jar,
    // we simply strip all entries from the client jar that are also present in the server jar
    // The client artifact would depend on the server artifact

    CompletableFuture<ResolutionResult<MinecraftEnvironment>> provide(final MinecraftPlatform side, final String version);

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
     * @param action the action needed to produce a variant, taking the input environment and a target path
     * @return the path of the jar
     */
    Path produceAssociatedArtifactSync(final MinecraftPlatform side, final String version, final String id, final BiConsumer<MinecraftEnvironment, Path> action)
        throws Exception;

    interface MinecraftEnvironment {

        Path jar();
        VersionDescriptor.Full metadata();
        CompletableFuture<MinecraftEnvironment> accessWidened(final Path... wideners);

    }

}
