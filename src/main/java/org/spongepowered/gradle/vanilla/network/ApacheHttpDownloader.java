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

import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.repository.ResolutionResult;
import org.spongepowered.gradle.vanilla.util.AsyncUtils;
import org.spongepowered.gradle.vanilla.util.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class ApacheHttpDownloader implements AutoCloseable, Downloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheHttpDownloader.class);

    private final Executor asyncExecutor;
    private final Path baseDirectory;
    private final CloseableHttpAsyncClient client;
    private final ResolveMode resolveMode;
    private final boolean writeToDisk;
    private final boolean shouldClose;

    /**
     * Create a downloader that does not cache.
     *
     * <p>The downloader's {@code resolveMode} is always {@link ResolveMode#REMOTE_ONLY}.</p>
     *
     * @param asyncExecutor the executor to execute on.
     * @return a new downloader
     */
    public static ApacheHttpDownloader uncached(final Executor asyncExecutor) {
        try {
            return new ApacheHttpDownloader(asyncExecutor, Files.createTempDirectory("downloader"), ResolveMode.REMOTE_ONLY, false);
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to create a temporary directory for file downloads");
        }
    }

    public ApacheHttpDownloader(final Executor asyncExecutor, final Path baseDirectory, final ResolveMode resolveMode) {
        this(asyncExecutor, baseDirectory, resolveMode, true);
    }

    private ApacheHttpDownloader(final Executor asyncExecutor, final Path baseDirectory, final ResolveMode resolveMode, final boolean writeToDisk) {
        this.asyncExecutor = asyncExecutor;
        this.baseDirectory = baseDirectory;
        this.resolveMode = resolveMode;
        this.writeToDisk = writeToDisk;

        // Configure the HTTP client
        // This won't actually launch a thread pool until the first request is performed.
        final IOReactorConfig config = IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofSeconds(5))
            .build();

        this.client = HttpAsyncClientBuilder.create()
            .setIOReactorConfig(config)
            .setUserAgent(Constants.USER_AGENT)
            .setRetryStrategy(new DefaultHttpRequestRetryStrategy(5, TimeValue.ofMilliseconds(500)))
            .build();
        this.shouldClose = true;
    }

    private ApacheHttpDownloader(final Executor asyncExecutor, final Path baseDirectory, final ResolveMode mode, final boolean writeToDisk, final CloseableHttpAsyncClient existing) {
        this.asyncExecutor = asyncExecutor;
        this.baseDirectory = baseDirectory;
        this.resolveMode = mode;
        this.writeToDisk = writeToDisk;
        this.client = existing;
        this.shouldClose = false;
    }

    public static BasicResponseConsumer<Path> responseToFileValidating(final Path path, final HashAlgorithm algorithm, final String hash) {
        try {
            return new BasicResponseConsumer<>(new ValidatingDigestingEntityConsumer<>(new ToPathEntityConsumer(path), algorithm, hash));
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Could not resolve hash algorithm");
        }
    }

    public CloseableHttpAsyncClient client() {
        this.client.start();
        return this.client;
    }

    @Override
    public Path baseDir() {
        return this.baseDirectory;
    }

    @Override
    public Downloader withBaseDir(final Path override) {
        Objects.requireNonNull(override, "override");
        return new ApacheHttpDownloader(
            this.asyncExecutor,
            override,
            this.resolveMode,
            this.writeToDisk,
            this.client
        );
    }

    @Override
    public CompletableFuture<ResolutionResult<String>> readString(final URL source, final String relativePath) {
        return this.download(source, this.baseDirectory.resolve(relativePath), path -> {
            final AsyncEntityConsumer<String> reader = new StringAsyncEntityConsumer();
            if (this.writeToDisk) {
                final AsyncEntityConsumer<Path> downloader = new ToPathEntityConsumer(path);
                return new TeeEntityConsumer<>(reader, downloader);
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
            final AsyncEntityConsumer<String> reader = new StringAsyncEntityConsumer();
            if (this.writeToDisk) {
                final AsyncEntityConsumer<Path> downloader = new ToPathEntityConsumer(path);
                return new TeeEntityConsumer<>(reader, downloader);
            } else {
                return reader;
            }
        }, this::readTextAsync);
    }

    private CompletableFuture<String> readTextAsync(final Path path) {
        return AsyncUtils.failableFuture(() -> {
            try (final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                final StringBuilder builder = new StringBuilder();
                final char[] buffer = new char[1024];
                int read;
                while ((read = reader.read(buffer)) > -1) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            }
        }, this.asyncExecutor);
    }


    @Override
    public CompletableFuture<ResolutionResult<byte[]>> readBytes(final URL source, final String relativePath) {
        return this.download(source, this.baseDirectory.resolve(relativePath), path -> {
            final AsyncEntityConsumer<byte[]> reader = new BasicAsyncEntityConsumer();
            if (this.writeToDisk) {
                final AsyncEntityConsumer<Path> downloader = new ToPathEntityConsumer(path);
                return new TeeEntityConsumer<>(reader, downloader);
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
            final AsyncEntityConsumer<byte[]> reader = new BasicAsyncEntityConsumer();
            if (this.writeToDisk) {
                final AsyncEntityConsumer<Path> downloader = new ToPathEntityConsumer(path);
                return new TeeEntityConsumer<>(reader, downloader);
            } else {
                return reader;
            }
        }, this::readBytesAsync);
    }

    private CompletableFuture<byte[]> readBytesAsync(final Path path) {
        return AsyncUtils.failableFuture(() -> Files.readAllBytes(path), this.asyncExecutor);
    }

    @Override
    public CompletableFuture<ResolutionResult<Path>> download(final URL source, final Path destination) {
        return this.download(
            source,
            destination,
            ToPathEntityConsumer::new,
            CompletableFuture::completedFuture
        );
    }

    @Override
    public CompletableFuture<ResolutionResult<Path>> downloadAndValidate(final URL source, final Path destination, final HashAlgorithm algorithm, final String hash) {
        return this.downloadValidating(
            source, destination, algorithm, hash,
            ToPathEntityConsumer::new,
            CompletableFuture::completedFuture
        );
    }

    // Shared logic

    private <T> CompletableFuture<ResolutionResult<T>> download(
        final URL source,
        final Path destination,
        final Function<Path, AsyncEntityConsumer<T>> responseConsumer,
        final Function<Path, CompletableFuture<T>> existingHandler
    ) {
        final File destFile = destination.toFile();
        final @Nullable BasicFileAttributes destAttributes = FileUtils.fileAttributesIfExists(destination);
        if (this.resolveMode != ResolveMode.REMOTE_ONLY && (destAttributes != null && destAttributes.isRegularFile())) { // TODO: check etag?
            // Check every 24 hours
            if (this.resolveMode == ResolveMode.LOCAL_ONLY
                || System.currentTimeMillis() - destAttributes.lastModifiedTime().toMillis() < Constants.Manifests.CACHE_TIMEOUT_SECONDS * 1000) {
                return existingHandler.apply(destination).thenApply(result -> ResolutionResult.result(result, true));
            }
        }

        if (this.resolveMode == ResolveMode.LOCAL_ONLY) {
            // No value in cache and we aren't able to resolve, so return a not found
            return CompletableFuture.completedFuture(ResolutionResult.notFound());
        }

        final FutureToCompletable<Message<HttpResponse, T>> result = new FutureToCompletable<>();
        try {
            this.client().execute(
                SimpleRequestProducer.create(SimpleHttpRequests.get(source.toURI())),
                new BasicResponseConsumer<>(responseConsumer.apply(destination)),
                result
            );
        } catch (final URISyntaxException ex) {
            result.future().completeExceptionally(ex);
        }
        return result.future().thenApply(message -> {
            switch (message.getHead().getCode()) {
                case HttpStatus.SC_NOT_FOUND:
                    return ResolutionResult.notFound();
                case HttpStatus.SC_OK:
                    // TODO: see if we can return an up-to-date result by comparing the digest of the download with the existing file?
                    return ResolutionResult.result(message.getBody(), false);
                default:
                    throw new CompletionException(new HttpErrorResponseException(source, message.getHead().getCode(), message.getHead().getReasonPhrase()));
            }
        });
    }

    private <T> CompletableFuture<ResolutionResult<T>> downloadValidating(
        final URL source,
        final Path destination,
        final HashAlgorithm algorithm,
        final String expectedHash,
        final Function<Path, AsyncEntityConsumer<T>> responseConsumer,
        final Function<Path, CompletableFuture<T>> existingHandler
    ) {
        final Path path = destination;
        if (path.toFile().isFile()) {
            // Validate that the file matches the path, only download if it doesn't.
            try {
                if (algorithm.validate(expectedHash, path)) {
                    return existingHandler.apply(path).thenApply(result -> ResolutionResult.result(result, true));
                } else {
                    ApacheHttpDownloader.LOGGER.warn("Found hash mismatch on file at {}, re-downloading", path);
                }
            } catch (final IOException ex) {
                ApacheHttpDownloader.LOGGER.warn("Failed to test hash on file at {}, re-downloading", path);
            }
            try {
                Files.deleteIfExists(path);
            } catch (final IOException ex) {
                ApacheHttpDownloader.LOGGER.warn("Failed to delete file at {}, will try to re-download anyways", path);
            }
        }

        if (this.resolveMode == ResolveMode.LOCAL_ONLY) {
            // No value in cache and we aren't able to resolve, so return a not found
            return CompletableFuture.completedFuture(ResolutionResult.notFound());
        }

        final FutureToCompletable<Message<HttpResponse, T>> result = new FutureToCompletable<>();
        try {
            this.client().execute(
                SimpleRequestProducer.create(SimpleHttpRequests.get(source.toURI())),
                new BasicResponseConsumer<>(new ValidatingDigestingEntityConsumer<>(responseConsumer.apply(path), algorithm, expectedHash)),
                result
            );
        } catch (final URISyntaxException | NoSuchAlgorithmException ex) {
            result.future().completeExceptionally(ex);
        }

        return result.future().thenApply(message -> {
            switch (message.getHead().getCode()) {
                case HttpStatus.SC_NOT_FOUND:
                    return ResolutionResult.notFound();
                case HttpStatus.SC_OK:
                    return ResolutionResult.result(message.getBody(), false); // Known invalid, hash does not match expected.
                default:
                    throw new CompletionException(new HttpErrorResponseException(source, message.getHead().getCode(), message.getHead().getReasonPhrase()));
            }
        });
    }

    @Override
    public void close() {
        if (this.shouldClose) {
            this.client.close(CloseMode.GRACEFUL);
        }
    }

}
