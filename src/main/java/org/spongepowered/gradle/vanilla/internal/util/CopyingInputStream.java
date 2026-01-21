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
package org.spongepowered.gradle.vanilla.internal.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Read from a stream, while writing the stream's contents to another output stream.
 */
public final class CopyingInputStream extends FilterInputStream {

    private final OutputStream copy;

    /**
     * Creates a <code>FilterInputStream</code> by assigning the argument
     * <code>in</code> to the field <code>this.in</code> so as to remember it for
     * later use.
     *
     * @param in the underlying input stream, or <code>null</code> if this instance
     *     is to be created without an underlying stream.
     * @param copy the output stream to copy writes to
     */
    public CopyingInputStream(final InputStream in, final OutputStream copy) {
        super(in);
        this.copy = copy;
    }

    @Override
    public int read() throws IOException {
        final int read = super.read();
        this.copy.write(read);
        return read;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        final int read = super.read(b);
        if (read != -1) {
            this.copy.write(b, 0, read);
        }
        return read;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int read = super.read(b, off, len);
        if (read != -1) {
            this.copy.write(b, off, read);
        }
        return read;
    }

    @Override
    public long skip(final long n) {
        return 0L;
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.copy.close();
    }

    @Override
    public void mark(final int readlimit) {
        // no-op
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("Mark not supported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
