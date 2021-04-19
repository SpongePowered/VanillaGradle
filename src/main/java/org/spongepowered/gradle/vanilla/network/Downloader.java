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
package org.spongepowered.gradle.vanilla.network;

import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Some sort of downloader for files.
 *
 * <p>Handles fetching strings and binary content from remotes as well.</p>
 *
 * <p>Access is uncached.</p>
 */
public interface Downloader {

    <T> CompletableFuture<T> downloadJson(final URL source, final String relativeLocation, final Class<T> type);

    <T> CompletableFuture<T> downloadJson(final URL source, final String relativeLocation, final HashAlgorithm algorithm, final String expectedHash, final Class<T> type);

    CompletableFuture<String> readString(final URL source);

    CompletableFuture<String> readStringAndValidate(final URL source, final HashAlgorithm algorithm, final String hash);

    CompletableFuture<byte[]> readBytes(final URL source);

    CompletableFuture<byte[]> readBytesAndValidate(final URL source, final HashAlgorithm algorithm, final String hash);

    /**
     * Download a file to the provided relative location.
     *
     * <p>If the file already exists, it may not be overwritten, depending on the downloader's caching policy.</p>
     *
     * @param source the URL to download from.
     * @param relativeLocation the location relative to the downloader's base directory.
     * @return a future returning the downloa
     */
    default CompletableFuture<Path> download(final URL source, final String relativeLocation) {
        return this.download(source, relativeLocation, false);
    }

    CompletableFuture<Path> download(final URL source, final String relativeLocation, boolean forceReplacement);

    CompletableFuture<Path> downloadAndValidate(final URL source, final String relativeLocation, final HashAlgorithm algorithm, final String hash);


}
