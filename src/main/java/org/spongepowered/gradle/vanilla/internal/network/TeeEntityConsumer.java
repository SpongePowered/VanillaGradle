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
package org.spongepowered.gradle.vanilla.internal.network.network;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Consume an entity with multiple consumers, returning a value from the
 * primary consumer.
 *
 * @param <P> the primary consumer type
 */
final class TeeEntityConsumer<P> implements AsyncEntityConsumer<P> {

    private final AsyncEntityConsumer<P> primary;
    private final AsyncEntityConsumer<?>[] alternates;

    TeeEntityConsumer(final AsyncEntityConsumer<P> primary, final AsyncEntityConsumer<?>... alternates) {
        this.primary = primary;
        this.alternates = alternates;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void streamStart(final EntityDetails entityDetails, final FutureCallback<P> resultCallback) throws HttpException, IOException {
        this.primary.streamStart(entityDetails, resultCallback);
        for (final AsyncEntityConsumer<?> alt : this.alternates) {
            alt.streamStart(entityDetails, (FutureCallback) NoOpCallback.INSTANCE);
        }
    }

    @Override
    public void failed(final Exception cause) {
        this.primary.failed(cause);
        for (final AsyncEntityConsumer<?> alt : this.alternates) {
            alt.failed(cause);
        }
    }

    @Override
    public P getContent() {
        for (final AsyncEntityConsumer<?> alt : this.alternates) {
            alt.getContent(); // in case the consumer performs any logic here
        }

        return this.primary.getContent();
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        // TODO: Try to calculate the minimum available capacity
        this.primary.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        final int position = src.position();
        this.primary.consume(src);
        for (final AsyncEntityConsumer<?> alt : this.alternates) {
            src.position(position); // restore position so everyone can read the same amount
            alt.consume(src);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        this.primary.streamEnd(trailers);
        for (final AsyncEntityConsumer<?> alt : this.alternates) {
            alt.streamEnd(trailers);
        }
    }

    @Override
    public void releaseResources() {
        this.primary.releaseResources();
        for (final AsyncEntityConsumer<?> alt : this.alternates) {
            alt.releaseResources();
        }
    }

    static class NoOpCallback implements FutureCallback<Void> {

        static final NoOpCallback INSTANCE = new NoOpCallback();

        private NoOpCallback() {
        }

        @Override
        public void completed(final Void result) {
        }

        @Override
        public void failed(final Exception ex) {
        }

        @Override
        public void cancelled() {

        }

    }

}
