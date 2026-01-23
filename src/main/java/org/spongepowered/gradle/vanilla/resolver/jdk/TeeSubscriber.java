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
package org.spongepowered.gradle.vanilla.resolver.jdk;

import org.jspecify.annotations.Nullable;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

class TeeSubscriber<V> implements HttpResponse.BodySubscriber<V> {
    private final HttpResponse.BodySubscriber<V> first;
    private final HttpResponse.BodySubscriber<?>[] others;
    private @Nullable ProxySubscription subscription;

    public TeeSubscriber(final HttpResponse.BodySubscriber<V> first, final HttpResponse.BodySubscriber<?>... others) {
        this.first = first;
        this.others = others;
    }

    @Override
    public CompletionStage<V> getBody() {
        return this.first.getBody();
    }

    @Override
    public void onSubscribe(final Flow.Subscription subscription) {
        final var proxySub = new ProxySubscription(subscription);
        this.subscription = proxySub;
        this.first.onSubscribe(proxySub);
        for (final var other : this.others) {
            other.onSubscribe(proxySub);
        }
        proxySub.flush();
    }

    @Override
    public void onNext(final List<ByteBuffer> item) {
        final var proxySub = this.subscription;

        final int[] positions = new int[item.size()];
        int i = 0;
        for (ByteBuffer buf : item) {
            positions[i++] = buf.position();
        }

        this.first.onNext(item);

        i = 0;
        for (ByteBuffer buf : item) {
            buf.position(positions[i++]);
        }

        for (final var other : this.others) {
            other.onNext(item);

            i = 0;
            for (ByteBuffer buf : item) {
                buf.position(positions[i++]);
            }
        }

        if (proxySub != null) {
            proxySub.flush();
        }
    }

    @Override
    public void onError(final Throwable throwable) {
        this.first.onError(throwable);
        for (final var other : this.others) {
            other.onError(throwable);
        }
    }

    @Override
    public void onComplete() {
        this.first.onComplete();
        for (final var other : this.others) {
            other.onComplete();
        }
    }

    static class ProxySubscription implements Flow.Subscription {
        private final Flow.Subscription original;
        private long requested;

        ProxySubscription(final Flow.Subscription original) {
            this.original = original;
        }

        @Override
        public void request(final long n) {
           this.requested = this.requested == 0 ? n : Math.min(this.requested, n);
        }

        void flush() {
            if (this.requested > 0) {
                this.original.request(this.requested);
                this.requested = 0;
            }
        }

        @Override
        public void cancel() {
            this.original.cancel();
        }
    }
}
