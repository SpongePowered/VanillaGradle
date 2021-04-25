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

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.net.URL;

/**
 * A reference to a downloadable {@link AssetIndex}.
 */
@Value.Immutable
@Gson.TypeAdapters
public interface AssetIndexReference {

    /**
     * An id used to refer to this asset index.
     *
     * @return the id
     */
    String id();

    /**
     * The sha1 hash of this asset index.
     *
     * @return the asset index hash
     */
    String sha1();

    /**
     * The size of the asset index file, in bytes.
     *
     * @return the file size
     */
    int size();

    /**
     * The total size of assets described by the index file, in bytes.
     *
     * @return the total size
     */
    int totalSize();

    /**
     * A url to download the asset index from.
     *
     * @return the download URL
     */
    URL url();

}
