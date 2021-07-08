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
package org.spongepowered.gradle.vanilla.resolver.jdk11;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.gradle.vanilla.internal.resolver.AsyncUtils;
import org.spongepowered.gradle.vanilla.internal.resolver.FileUtils;
import org.spongepowered.gradle.vanilla.resolver.Downloader;
import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;
import org.spongepowered.gradle.vanilla.resolver.HttpErrorResponseException;
import org.spongepowered.gradle.vanilla.resolver.ResolutionResult;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class JdkHttpClientDownloader implements Downloader {
    public static final long CACHE_TIMEOUT_SECONDS = 24 /* hours */ * 60 /* minutes/hr */ * 60 /* seconds/min */; // todo: replace with ETag
    private static final System.Logger LOGGER = System.getLogger(JdkHttpClientDownloader.class.getName());

    private final Executor asyncExecutor;
    private final Path baseDirectory;
    private final HttpClient client;
    private final ResolveMode resolveMode;
    private final boolean writeToDisk;

    /**
     * Create a downloader that does not cache.
     *
     * <p>The downloader's {@code resolveMode} is always {@link ResolveMode#REMOTE_ONLY}.</p>
     *
     * @param asyncExecutor the executor to execute on.
     * @return a new downloader
     */
    public static JdkHttpClientDownloader uncached(final Executor asyncExecutor) {
        try {
            return new JdkHttpClientDownloader(asyncExecutor, Files.createTempDirectory("downloader"), ResolveMode.REMOTE_ONLY, false);
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to create a temporary directory for file downloads");
        }
    }

    public JdkHttpClientDownloader(final Executor asyncExecutor, final Path baseDirectory, final ResolveMode resolveMode) {
        this(asyncExecutor, baseDirectory, resolveMode, true);
    }

    private JdkHttpClientDownloader(final Executor asyncExecutor, final Path baseDirectory, final ResolveMode resolveMode, final boolean writeToDisk) {
        this.asyncExecutor = asyncExecutor;
        this.baseDirectory = baseDirectory;
        this.resolveMode = resolveMode;
        this.writeToDisk = writeToDisk;

        // Configure the HTTP client
        // This won't actually launch a thread pool until the first request is performed.
        this.client = HttpClient.newBuilder()
            .executor(this.asyncExecutor)
            .connectTimeout(Duration.of(5, ChronoUnit.SECONDS))
            .build();
    }

    private JdkHttpClientDownloader(final Executor asyncExecutor, final Path baseDirectory, final ResolveMode mode, final boolean writeToDisk, final HttpClient existing) {
        this.asyncExecutor = asyncExecutor;
        this.baseDirectory = baseDirectory;
        this.resolveMode = mode;
        this.writeToDisk = writeToDisk;
        this.client = existing;
    }

    @Override
    public Path baseDir() {
        return this.baseDirectory;
    }

    @Override
    public Downloader withBaseDir(final Path override) {
        return new JdkHttpClientDownloader(this.asyncExecutor, Objects.requireNonNull(override, "override"), this.resolveMode, this.writeToDisk, this.client);
    }

    @Override
    public CompletableFuture<ResolutionResult<String>> readString(final URL source, final String relativePath) {
        return this.download(source, this.baseDirectory.resolve(relativePath), path -> {
            final HttpResponse.BodyHandler<String> reader = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
            if (this.writeToDisk) {
                final HttpResponse.BodyHandler<Path> downloader = HttpResponse.BodyHandlers.ofFile(path);
                return info -> new TeeSubscriber<>(reader.apply(info), downloader.apply(info));
            } else {
                return reader;
            }
        }, this::readTextAsync);
    }

    @Override
    public CompletableFuture<ResolutionResult<String>> readStringAndValidate(
        final URL source, final String relativePath, final HashAlgorithm algorithm, final String hash
    ) {
        return this.downloadValidating(source, this.baseDirectory.resolve(relativePath), algorithm, hash, path -> {
            final HttpResponse.BodyHandler<String> reader = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
            if (this.writeToDisk) {
                final HttpResponse.BodyHandler<Path> downloader = HttpResponse.BodyHandlers.ofFile(path);
                return info -> new TeeSubscriber<>(reader.apply(info), downloader.apply(info));
            } else {
                return reader;
            }
        }, this::readTextAsync);
    }

    private CompletableFuture<String> readTextAsync(final Path path) {
        return AsyncUtils.failableFuture(() -> Files.readString(path, StandardCharsets.UTF_8), this.asyncExecutor);
    }

    @Override
    public CompletableFuture<ResolutionResult<byte[]>> readBytes(final URL source, final String relativePath) {
        return this.download(source, this.baseDirectory.resolve(relativePath), path -> {
            final HttpResponse.BodyHandler<byte[]> reader = HttpResponse.BodyHandlers.ofByteArray();
            if (this.writeToDisk) {
                final HttpResponse.BodyHandler<Path> downloader = HttpResponse.BodyHandlers.ofFile(path);
                return info -> new TeeSubscriber<>(reader.apply(info), downloader.apply(info));
            } else {
                return reader;
            }
        }, this::readBytesAsync);
    }

    @Override
    public CompletableFuture<ResolutionResult<byte[]>> readBytesAndValidate(
        final URL source, final String relativePath, final HashAlgorithm algorithm, final String hash
    ) {
        return this.downloadValidating(source, this.baseDirectory.resolve(relativePath), algorithm, hash, path -> {
            final HttpResponse.BodyHandler<byte[]> reader = HttpResponse.BodyHandlers.ofByteArray();
            if (this.writeToDisk) {
                final HttpResponse.BodyHandler<Path> downloader = HttpResponse.BodyHandlers.ofFile(path);
                return info -> new TeeSubscriber<>(reader.apply(info), downloader.apply(info));
            } else {
                return reader;
            }
        }, this::readBytesAsync);
    }

    private CompletableFuture<byte[]> readBytesAsync(final Path path) {
        return AsyncUtils.failableFuture(() -> Files.readAllBytes(path), this.asyncExecutor);
    }

    @Override
    public CompletableFuture<ResolutionResult<Path>> download(final URL source, final String destination) {
        return this.download(
            source,
            this.baseDirectory.resolve(destination),
            HttpResponse.BodyHandlers::ofFile,
            CompletableFuture::completedFuture
        );
    }

    @Override
    public CompletableFuture<ResolutionResult<Path>> downloadAndValidate(
        final URL source, final String destination, final HashAlgorithm algorithm, final String hash
    ) {
        return this.downloadValidating(
            source,
            this.baseDirectory.resolve(destination),
            algorithm,
            hash,
            HttpResponse.BodyHandlers::ofFile,
            CompletableFuture::completedFuture
        );
    }

    // Shared logic

    private <T> CompletableFuture<ResolutionResult<T>> download(
        final URL source,
        final Path destination,
        final Function<Path, HttpResponse.BodyHandler<T>> responseConsumer,
        final Function<Path, CompletableFuture<T>> existingHandler
    ) {
        final @Nullable BasicFileAttributes destAttributes = FileUtils.fileAttributesIfExists(destination);
        if (this.resolveMode != ResolveMode.REMOTE_ONLY && (destAttributes != null && destAttributes.isRegularFile())) { // TODO: check etag?
            // Check every 24 hours
            if (this.resolveMode == ResolveMode.LOCAL_ONLY
                || System.currentTimeMillis() - destAttributes.lastModifiedTime().toMillis() < JdkHttpClientDownloader.CACHE_TIMEOUT_SECONDS * 1000) {
                return existingHandler.apply(destination).thenApply(result -> ResolutionResult.result(result, true));
            }
        }

        if (this.resolveMode == ResolveMode.LOCAL_ONLY) {
            // No value in cache and we aren't able to resolve, so return a not found
            return CompletableFuture.completedFuture(ResolutionResult.notFound());
        }

        final CompletableFuture<HttpResponse<T>> result;
        try {
            result = this.client.sendAsync(
                this.makeRequest(source, null), // todo: etag
                responseConsumer.apply(destination));
        } catch (final URISyntaxException ex) {
            return CompletableFuture.failedFuture(ex);
        }
        return result.thenApply(message -> {
            switch (message.statusCode()) {
                case 404:
                    return ResolutionResult.notFound();
                case 200:
                    return ResolutionResult.result(message.body(), false);
                default:
                    throw new CompletionException(new HttpErrorResponseException(source, message.statusCode(), String.valueOf(message.statusCode())));
            }
        });
    }

    private <T> CompletableFuture<ResolutionResult<T>> downloadValidating(
        final URL source,
        final Path destination,
        final HashAlgorithm algorithm,
        final String expectedHash,
        final Function<Path, HttpResponse.BodyHandler<T>> responseConsumer,
        final Function<Path, CompletableFuture<T>> existingHandler
    ) {
        final Path path = destination;
        if (path.toFile().isFile()) {
            // Validate that the file matches the path, only download if it doesn't.
            try {
                if (algorithm.validate(expectedHash, path)) {
                    return existingHandler.apply(path).thenApply(result -> ResolutionResult.result(result, true));
                } else {
                    JdkHttpClientDownloader.LOGGER.log(System.Logger.Level.WARNING, "Found hash mismatch on file at {}, re-downloading", path);
                }
            } catch (final IOException ex) {
                JdkHttpClientDownloader.LOGGER.log(System.Logger.Level.WARNING, "Failed to test hash on file at {}, re-downloading", path);
            }
            try {
                Files.deleteIfExists(path);
            } catch (final IOException ex) {
                JdkHttpClientDownloader.LOGGER.log(System.Logger.Level.WARNING, "Failed to delete file at {}, will try to re-download anyways", path);
            }
        }

        if (this.resolveMode == ResolveMode.LOCAL_ONLY) {
            // No value in cache and we aren't able to resolve, so return a not found
            return CompletableFuture.completedFuture(ResolutionResult.notFound());
        }

        final CompletableFuture<HttpResponse<T>> result;
        try {
            result = this.client.sendAsync(
                this.makeRequest(source, null),
                JdkHttpClientDownloader.validating(responseConsumer.apply(path), algorithm, expectedHash)
            );
        } catch (final URISyntaxException ex) {
            return CompletableFuture.failedFuture(ex);
        }

        return result.thenApply(message -> {
            switch (message.statusCode()) {
                case HttpConstants.STATUS_NOT_FOUND:
                    return ResolutionResult.notFound();
                case HttpConstants.STATUS_OK:
                    return ResolutionResult.result(message.body(), false); // Known invalid, hash does not match expected.
                default:
                    throw new CompletionException(new HttpErrorResponseException(source, message.statusCode(), message.toString()));
            }
        });
    }

    private HttpRequest makeRequest(final URL url, final @Nullable String etag) throws URISyntaxException {
        final var requestBuilder = HttpRequest.newBuilder()
            .GET()
            .uri(url.toURI());

        if (etag != null) {
            requestBuilder.header(HttpConstants.HEADER_IF_NONE_MATCH, etag);
        }
        return requestBuilder.build();
    }

    @Override
    public void close() throws IOException {
        // nothing needed, the client just relies on the executor
    }

    // body subscribers
    static <T> HttpResponse.BodyHandler<T> validating(final HttpResponse.BodyHandler<T> original, final HashAlgorithm algo, final String expectedHash) {
        return info -> JdkHttpClientDownloader.validating(original.apply(info), algo, expectedHash);
    }

    static <T> HttpResponse.BodySubscriber<T> validating(final HttpResponse.BodySubscriber<T> original, final HashAlgorithm algo, final String expectedHash) {
        return new ValidatingBodySubscriber<>(algo, original, expectedHash);
    }

}
