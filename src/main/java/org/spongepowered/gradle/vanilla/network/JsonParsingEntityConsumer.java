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
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.entity.AbstractCharAsyncEntityConsumer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.CharBuffer;

public class JsonParsingEntityConsumer<T> extends AbstractCharAsyncEntityConsumer<T> {
    private static final int BUFFER_SIZE = 4096;

    private final Gson gson;
    private final Type expectedType;
    private StringBuilder builder;
    private T result;

    public JsonParsingEntityConsumer(final Gson gson, final Class<T> type) {
        this.gson = gson;
        this.expectedType = type;
    }

    @Override
    protected void streamStart(final ContentType contentType) throws HttpException {
        if (this.builder != null) {
            throw new IllegalStateException("Tried to start a stream while an existing one was still being processed");
        }
        if (!contentType.isSameMimeType(ContentType.APPLICATION_JSON)) {
            throw new HttpException("Expected content type of " + ContentType.APPLICATION_JSON + " but got " + contentType);
        }
        this.builder = new StringBuilder();
        this.result = null;
    }

    @Override
    protected T generateContent() throws IOException {
        if (this.result == null) {
            throw new IOException("No result was present");
        }
        final T result = this.result;
        this.result = null;
        return result;
    }

    @Override
    protected int capacityIncrement() {
        return JsonParsingEntityConsumer.BUFFER_SIZE;
    }

    @Override
    protected void data(final CharBuffer src, final boolean endOfStream) throws IOException {
        if (this.builder == null) {
            throw new IOException("Tried to receive data before any was present");
        }
        this.builder.append(src);
        if (endOfStream) {
            this.result = this.gson.fromJson(this.builder.toString(), this.expectedType);
        }
    }

    @Override
    public void releaseResources() {
        this.builder = null;
    }
}
