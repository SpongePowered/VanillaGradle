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

import org.cadixdev.atlas.Atlas;
import org.cadixdev.atlas.AtlasTransformerContext;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Some sort of operation that can be performed on a jar, via {@link Atlas}.
 */
public interface ArtifactModifier {

    char ENTRY_SEPARATOR = '_';

    char KEY_VALUE_SEPARATOR = '-';

    /**
     * A short (1-3 character) identifier for this type of
     * artifact transformation.
     *
     * @return the type identifier
     */
    String key();

    /**
     * A key indicating the current state of this artifact's inputs.
     *
     * <p>This value should be cached if it is expensive to calculate.</p>
     *
     * @return a key capturing this transformation's inputs
     */
    String stateKey();

    /**
     * Create a new populator for performing transformations. 
     * 
     * <p>This will always be called from the thread where resolution is initiated.</p> 
     * 
     * <p>The populator returned must remain valid until it is closed.</p>
     *
     * @param context the context available when preparing a populator, safe to use
     *     asynchronously
     * @return a future providing the populator
     */
    CompletableFuture<AtlasPopulator> providePopulator(final MinecraftResolver.Context context);

    /**
     * Indicates that the result of this modification should be stored in the
     * project-local storage, rather than global storage.
     *
     * @return whether local storage is required
     */
    boolean requiresLocalStorage();

    /**
     * A function that can populate an {@link Atlas} instance.
     */
    @FunctionalInterface
    interface AtlasPopulator extends AutoCloseable {
        JarEntryTransformer provide(final AtlasTransformerContext context);

        @Override
        default void close() throws IOException {
        }
    }

}
