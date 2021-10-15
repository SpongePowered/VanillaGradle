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

import org.immutables.value.Value;

/**
 * A single entry in a bundle.
 *
 * <p>In bundle format version {@code 1.0}, these elements are declared as
 * rows of tab-separated values.</p>
 */
@Value.Immutable
public interface BundleElement {

    /**
     * Create a new bundle element.
     *
     * @param sha256 the sha-265 hash
     * @param id the ID of the bundle element
     * @param path the path in the jar
     * @return a new element
     */
    static BundleElement of(final String sha256, final String id, final String path) {
        return new BundleElementImpl(sha256, id, path);
    }

    /**
     * The expected hash of the file in the jar.
     *
     * @return SHA-265 hash string
     */
    @Value.Parameter
    String sha256();

    /**
     * The maven coordinates corresponding to this artifact.
     *
     * @return the maven coordinates
     */
    @Value.Parameter
    String id();

    /**
     * The path of the index entry in the jar.
     *
     * @return the path relative to the jar
     */
    @Value.Parameter
    String path();

}
