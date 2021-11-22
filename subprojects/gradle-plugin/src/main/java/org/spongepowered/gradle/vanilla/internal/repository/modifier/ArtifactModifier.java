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
package org.spongepowered.gradle.vanilla.internal.repository.modifier;

import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.api.Transformer;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Some sort of operation that can be performed on a jar, via a {@link Renamer}.
 */
public interface ArtifactModifier {

    char ENTRY_SEPARATOR = '_';

    char KEY_VALUE_SEPARATOR = '-';

    static String decorateArtifactId(final String originalId, final Set<ArtifactModifier> modifiers) {
        if (modifiers.isEmpty()) {
            return originalId;
        }

        // We need to compute our own key to be able to query the map
        final StringBuilder decoratedArtifactBuilder = new StringBuilder(originalId.length() + 10 * modifiers.size());
        decoratedArtifactBuilder.append(originalId);
        for (final ArtifactModifier modifier : modifiers) {
            decoratedArtifactBuilder.append(ArtifactModifier.ENTRY_SEPARATOR)
                .append(modifier.key())
                .append(ArtifactModifier.KEY_VALUE_SEPARATOR)
                .append(modifier.stateKey());
        }
        return decoratedArtifactBuilder.toString();
    }

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
    CompletableFuture<TransformerProvider> providePopulator(final MinecraftResolver.Context context);

    /**
     * Indicates that the result of this modification should be stored in the
     * project-local storage, rather than global storage.
     *
     * @return whether local storage is required
     */
    boolean requiresLocalStorage();

    /**
     * A function that can provide a {@link Transformer} for use with a renamer, optionally having a post-rename operation to clean up resources.
     */
    @FunctionalInterface
    interface TransformerProvider extends AutoCloseable {
        Transformer provide();

        @Override
        default void close() throws IOException {
        }
    }

}
