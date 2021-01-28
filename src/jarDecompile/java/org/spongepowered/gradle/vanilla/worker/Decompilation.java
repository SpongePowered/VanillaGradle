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
package org.spongepowered.gradle.vanilla.worker;

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Decompilation {

    public static CloseableBytecodeProvider bytecodeFromJar() {
        return new CloseableBytecodeProvider() {
            private final ConcurrentMap<String, ZipFile> files = new ConcurrentHashMap<>();
            @Override
            public void close() throws IOException {
                IOException error = null;
                for (final ZipFile file : this.files.values()) {
                    try {
                        file.close();
                    } catch (final IOException ex) {
                        if (error != null) {
                            error.addSuppressed(ex);
                        } else {
                            error = ex;
                        }
                    }
                }
                this.files.clear();
                if (error != null) {
                    throw error;
                }
            }

            @Override
            public byte[] getBytecode(final String external, final String internal) throws IOException {
                try {
                    final ZipFile file = this.files.computeIfAbsent(external, path -> {
                        try {
                            return new ZipFile(path);
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    final ZipEntry entry = file.getEntry(internal);
                    return InterpreterUtil.getBytes(file, entry);
                } catch (final RuntimeException ex) {
                    if (ex.getCause() instanceof IOException) {
                        throw (IOException) ex.getCause();
                    } else {
                        throw ex;
                    }
                }
            }
        };
    }

    public interface CloseableBytecodeProvider extends IBytecodeProvider, AutoCloseable {
        @Override
        void close() throws IOException;
    }
}