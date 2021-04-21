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

import org.spongepowered.gradle.vanilla.repository.ResolutionResult;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Some sort of downloader for files.
 *
 * <p>Handles fetching strings and binary content from remotes as well.</p>
 *
 * <p>Access may be cached.</p>
 */
public interface Downloader extends AutoCloseable {

    /**
     * Get the base directory used for this downloader.
     *
     * @return the base directory
     */
    Path baseDir();

    /**
     * Return a new downloader with a new base directory, but sharing this
     * downloader's resources.
     *
     * <p>Closing the returned downloader must not have any effect on
     * this instance.</p>
     *
     * @param override the new base directory
     * @return a derived downloader
     */
    Downloader withBaseDir(final Path override);

    /**
     * Read the contents of {@code source} as a {@link String}.
     *
     * <p>Encoding will be determined based on response headers, defaulting to
     * UTF-8.</p>
     *
     * @param source the URL to download from
     * @param relativePath the location to use as a cache key for the result
     * @return a future providing the completed result
     */
    CompletableFuture<ResolutionResult<String>> readString(final URL source, final String relativePath);

    /**
     * Read the contents of {@code source} as a {@link String}.
     *
     * <p>The downloaded content and any cached information will be validated
     * against the provided hash.</p>
     *
     * @param source the URL to download from
     * @param relativePath the location to use as a cache key for the result
     * @param algorithm the hash algorithm to test with
     * @param hash the expected hash, as a string of hex digits
     * @return a future providing the resolved result
     */
    CompletableFuture<ResolutionResult<String>> readStringAndValidate(final URL source, final String relativePath, final HashAlgorithm algorithm, final String hash);

    /**
     * Read the contents of {@code source} as a {@code byte[]}.
     *
     * @param source the URL to download from
     * @param relativePath the location to use as a cache key for the result
     * @return a future providing the completed result
     */
    CompletableFuture<ResolutionResult<byte[]>> readBytes(final URL source, final String relativePath);

    /**
     * Read the contents of {@code source} as a {@code byte[]}.
     *
     * <p>The downloaded content and any cached information will be validated
     * against the provided hash.</p>
     *
     * @param source the URL to download from
     * @param relativePath the location to use as a cache key for the result
     * @param algorithm the hash algorithm to test with
     * @param hash the expected hash, as a string of hex digits
     * @return a future providing the resolved result
     */
    CompletableFuture<ResolutionResult<byte[]>> readBytesAndValidate(final URL source, final String relativePath, final HashAlgorithm algorithm, final String hash);

    /**
     * Download a file to the provided relative location.
     *
     * <p>If the file already exists, it may not be overwritten, depending on
     * the downloader's caching policy.</p>
     *
     * @param source the URL to download from.
     * @param destination the location to download to
     * @return a future returning the downloaded patch once a download is complete
     */
    CompletableFuture<ResolutionResult<Path>> download(final URL source, final Path destination);

    CompletableFuture<ResolutionResult<Path>> downloadAndValidate(final URL source, final Path destination, final HashAlgorithm algorithm, final String hash);

    @Override
    void close() throws IOException;

    /**
     * A mode to control usage of any potential locale cache
     */
    enum ResolveMode {
        /**
         * Test and validate local before querying remote.
         */
        LOCAL_THEN_REMOTE,
        /**
         * Only check the local storage.
         *
         * <p>In this mode, hash validation failures will result in a printed
         * warning rather than an error.</p>
         */
        LOCAL_ONLY,

        /**
         * Ignore any existing local state and always resolve from the remote.
         */
        REMOTE_ONLY,
    }
}
