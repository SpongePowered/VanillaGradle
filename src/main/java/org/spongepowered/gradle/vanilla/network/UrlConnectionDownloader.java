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

import com.google.gson.Gson;
import org.spongepowered.gradle.vanilla.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class UrlConnectionDownloader implements Downloader {
    private final Path baseDirectory;
    private final Gson gson;
    private final Executor executor;

    public UrlConnectionDownloader(final Path baseDirectory, final Gson gson, final Executor executor) {
        this.baseDirectory = baseDirectory;
        this.gson = gson;
        this.executor = executor;
    }

    @Override
    public <T> CompletableFuture<T> downloadJson(
        final URL source, final String relativeLocation, final Class<T> type
    ) {
        final CompletableFuture<T> output = new CompletableFuture<>();
        this.executor.execute(() -> {
            try {
                final URLConnection conn = source.openConnection();
                if (conn instanceof HttpURLConnection) {
                    conn.setRequestProperty("User-Agent", Constants.USER_AGENT);
                }

                try (final InputStream in = conn.getInputStream(); final BufferedReader reader = new BufferedReader(new InputStreamReader(in, conn.getContentEncoding()))) {
                    output.complete(this.gson.fromJson(reader, type));
                }

            } catch (final IOException ex) {
                output.completeExceptionally(ex);
            }
        });
        return output;
    }

    @Override
    public <T> CompletableFuture<T> downloadJson(
        final URL source, final String relativeLocation, final HashAlgorithm algorithm, final String expectedHash, final Class<T> type
    ) {
        final CompletableFuture<T> output = new CompletableFuture<>();
        this.executor.execute(() -> {
            try {
                final URLConnection conn = source.openConnection();
                if (conn instanceof HttpURLConnection) {
                    conn.setRequestProperty("User-Agent", Constants.USER_AGENT);
                }

                try (final InputStream in = conn.getInputStream();
                     final DigestInputStream digestIn = algorithm.validate(in);
                     final BufferedReader reader = new BufferedReader(new InputStreamReader(digestIn, conn.getContentEncoding()))) {
                    final T value = this.gson.fromJson(reader, type);
                    final String actualHash = HashAlgorithm.toHexString(digestIn.getMessageDigest().digest());
                    if (HashAlgorithm.toHexString(digestIn.getMessageDigest().digest()).equals(expectedHash)) {
                        output.complete(value);
                    } else {
                        output.completeExceptionally(new IOException("Failed to download from " + source + ", got a hash of "+ actualHash + " but expected " + expectedHash));
                    }
                }
            } catch (final IOException ex) {
                output.completeExceptionally(ex);
            }
        });
        return output;
    }

    @Override
    public CompletableFuture<String> readString(final URL source) {
        return null;
    }

    @Override
    public CompletableFuture<String> readStringAndValidate(final URL source, final HashAlgorithm algorithm, final String hash) {
        return null;
    }

    @Override
    public CompletableFuture<byte[]> readBytes(final URL source) {
        return null;
    }

    @Override
    public CompletableFuture<byte[]> readBytesAndValidate(final URL source, final HashAlgorithm algorithm, final String hash) {
        return null;
    }

    @Override
    public CompletableFuture<Path> download(final URL source, final String relativeLocation) {
        final Path expected = this.baseDirectory.resolve(relativeLocation);
        final CompletableFuture<Path> output = new CompletableFuture<>();
        this.executor.execute(() -> {
            try {
                final URLConnection conn = source.openConnection();
                if (conn instanceof HttpURLConnection) {
                    conn.setRequestProperty("User-Agent", Constants.USER_AGENT);
                }

                try (final InputStream in = conn.getInputStream(); final OutputStream os = Files.newOutputStream(expected)) {
                    final byte[] data = new byte[8192];
                    int read;
                    while ((read = in.read(data)) > -1) {
                        os.write(data, 0, read);
                    }
                }

                output.complete(expected);
            } catch (final IOException ex) {
                output.completeExceptionally(ex);
            }
        });
        return output;
    }

    @Override
    public CompletableFuture<Path> download(final URL source, final String relativeLocation, final boolean forceReplacement) {
        return null;
    }

    @Override
    public CompletableFuture<Path> downloadAndValidate(
        final URL source, final String relativeLocation, final HashAlgorithm algorithm, final String hash
    ) {
        final Path expected = this.baseDirectory.resolve(relativeLocation);
        final CompletableFuture<Path> output = new CompletableFuture<>();
        this.executor.execute(() -> {
            try {
                final URLConnection conn = source.openConnection();
                if (conn instanceof HttpURLConnection) {
                    conn.setRequestProperty("User-Agent", Constants.USER_AGENT);
                }

                try (final InputStream in = conn.getInputStream();
                     final DigestInputStream digestIn = algorithm.validate(in);
                     final OutputStream os = Files.newOutputStream(expected)) {
                    final byte[] data = new byte[8192];
                    int read;
                    while ((read = digestIn.read(data)) > -1) {
                        os.write(data, 0, read);
                    }

                    // TODO: write to temp directory

                    final String actualHash = HashAlgorithm.toHexString(digestIn.getMessageDigest().digest());
                    if (!HashAlgorithm.toHexString(digestIn.getMessageDigest().digest()).equals(hash)) {
                        output.completeExceptionally(new IOException("Failed to download from " + source + ", got a hash of "+ actualHash + " but expected " + hash));
                        return;
                    }
                }
                output.complete(expected);
            } catch (final IOException ex) {
                output.completeExceptionally(ex);
            }
        });
        return output;
    }
}
