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

import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

class ValidatingBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {

    private final HttpResponse.BodySubscriber<T> original;
    private final HashAlgorithm algorithm;
    private final MessageDigest md;
    private final String expectedHash;

    public ValidatingBodySubscriber(final HashAlgorithm algo, final HttpResponse.BodySubscriber<T> original, final String expectedHash) {
        this.original = original;
        this.algorithm = algo;
        this.md = algo.digest();
        this.expectedHash = expectedHash;
    }

    @Override
    public CompletionStage<T> getBody() {
        return this.original.getBody();
    }

    @Override
    public void onSubscribe(final Flow.Subscription subscription) {
        this.original.onSubscribe(subscription);
    }

    @Override
    public void onNext(final List<ByteBuffer> item) {
        for (final ByteBuffer buf : item) {
            final int pos = buf.position();
            this.md.update(buf);
            buf.position(pos);
        }
        this.original.onNext(item);
    }

    @Override
    public void onError(final Throwable throwable) {
        this.original.onError(throwable);
    }

    @Override
    public void onComplete() {
        final String actual = HashAlgorithm.toHexString(this.md.digest());
        if (!actual.equals(this.expectedHash)) {
            this.original.onError(new IOException("Failed to validate " + this.algorithm.digestName() + " hash. Expected " + this.expectedHash + ", but got " + actual));
        } else {
            this.original.onComplete();
        }
    }
}
