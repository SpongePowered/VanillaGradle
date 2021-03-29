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
package org.spongepowered.gradle.vanilla.storage;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.DigestingEntityConsumer;
import org.spongepowered.gradle.vanilla.util.DigestUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

final class Sha1ValidatingDigestingEntityConsumer<V> extends DigestingEntityConsumer<V> {
    private final String expected;

    public Sha1ValidatingDigestingEntityConsumer(final AsyncEntityConsumer<V> wrapped, final String expected)
        throws NoSuchAlgorithmException {
        super("SHA-1", wrapped);
        this.expected = expected;
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        super.streamEnd(trailers);
        final String actual = DigestUtils.toHexString(this.getDigest());
        if (!DigestUtils.toHexString(this.getDigest()).equals(this.expected)) {
            throw new IOException("Failed to validate SHA-1 hash. Expected " + this.expected + ", but got " + actual);
        }
    }
}
