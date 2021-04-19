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
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.util.GsonUtils;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;

public class ApacheHttpDownloader implements AutoCloseable, Downloader {
    private final Path baseDirectory;
    private final CloseableHttpAsyncClient client;

    public ApacheHttpDownloader(final Path baseDirectory) {
        this.baseDirectory = baseDirectory;
        // TODO: Custom TLS strategy needed?
        // https://github.com/apache/httpcomponents-client/blob/5.0.x/httpclient5/src/test/java/org/apache/hc/client5/http/examples/AsyncClientTlsAlpn.java#L57
        final IOReactorConfig config = IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofSeconds(5))
            .build();

        this.client = HttpAsyncClientBuilder.create()
            .setIOReactorConfig(config)
            .setUserAgent(Constants.USER_AGENT)
            .setRetryStrategy(new DefaultHttpRequestRetryStrategy(5, TimeValue.ofMilliseconds(500)))
            .build();

    }

    public static BasicResponseConsumer<Path> responseToFile(final Path path) {
        return new BasicResponseConsumer<>(new ToPathEntityConsumer(path));
    }

    public static BasicResponseConsumer<Path> responseToFileValidating(final Path path, final HashAlgorithm algorithm, final String hash) {
        try {
            return new BasicResponseConsumer<>(new ValidatingDigestingEntityConsumer<>(new ToPathEntityConsumer(path), algorithm, hash));
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Could not resolve hash algorithm");
        }
    }

    public static <T> BasicResponseConsumer<T> responseToJson(final Class<T> type) {
        return new BasicResponseConsumer<>(new JsonParsingEntityConsumer<>(GsonUtils.GSON, type));
    }

    public static <T> BasicResponseConsumer<T> responseToJsonValidating(final Class<T> type, final HashAlgorithm algorithm, final String hash) {
        try {
            return new BasicResponseConsumer<>(new ValidatingDigestingEntityConsumer<>(new JsonParsingEntityConsumer<>(GsonUtils.GSON, type), algorithm, hash));
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Could not resolve hash algorithm");
        }
    }

    public CloseableHttpAsyncClient client() {
        this.client.start();
        return this.client;
    }

    @Override
    public <T> CompletableFuture<T> downloadJson(final URL source, final String relativeLocation, final Class<T> type) {
        final FutureToCompletable<Message<HttpResponse, T>> result = new FutureToCompletable<>();
        try {
            this.client().execute(
                SimpleRequestProducer.create(SimpleHttpRequests.get(source.toURI())),
                ApacheHttpDownloader.responseToJson(type),
                result
            );
        } catch (final URISyntaxException ex) {
            result.future().completeExceptionally(ex);
        }
        return result.future().thenApply(Message::getBody);
    }

    @Override
    public <T> CompletableFuture<T> downloadJson(
        final URL source, final String relativeLocation, final HashAlgorithm algorithm, final String expectedHash, final Class<T> type
    ) {
        final FutureToCompletable<Message<HttpResponse, T>> result = new FutureToCompletable<>();
        try {
            this.client().execute(
                SimpleRequestProducer.create(SimpleHttpRequests.get(source.toURI())),
                ApacheHttpDownloader.responseToJson(type),
                result
            );
        } catch (final URISyntaxException ex) {
            result.future().completeExceptionally(ex);
        }
        return result.future().thenApply(Message::getBody);
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
    public CompletableFuture<Path> download(final URL source, final String relativePath, final boolean forceReplacement) {
        final Path path = this.baseDirectory.resolve(relativePath);
        if (!forceReplacement && Files.isRegularFile(path)) {
            return CompletableFuture.completedFuture(path);
        }

        final FutureToCompletable<Message<HttpResponse, Path>> result = new FutureToCompletable<>();
        try {
            this.client().execute(
                SimpleRequestProducer.create(SimpleHttpRequests.get(source.toURI())),
                ApacheHttpDownloader.responseToFile(path),
                result
            );
        } catch (final URISyntaxException ex) {
            result.future().completeExceptionally(ex);
        }
        return result.future().thenApply(Message::getBody);
    }

    @Override
    public CompletableFuture<Path> downloadAndValidate(final URL source, final String relativePath, final HashAlgorithm algorithm, final String hash) {
        final Path path = this.baseDirectory.resolve(relativePath);
        if (Files.isRegularFile(path)) {
            // Validate that the file matches the path, only download if it doesn't.
            return CompletableFuture.completedFuture(path);
        }

        final FutureToCompletable<Message<HttpResponse, Path>> result = new FutureToCompletable<>();
        try {
            this.client().execute(
                SimpleRequestProducer.create(SimpleHttpRequests.get(source.toURI())),
                ApacheHttpDownloader.responseToFileValidating(path, algorithm, hash),
                result
            );
        } catch (final URISyntaxException ex) {
            result.future().completeExceptionally(ex);
        }
        return result.future().thenApply(Message::getBody);
    }

    @Override
    public void close() {
        this.client.close(CloseMode.GRACEFUL);
    }
}
