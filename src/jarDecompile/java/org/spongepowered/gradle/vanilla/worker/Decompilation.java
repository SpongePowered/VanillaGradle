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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Decompilation {

    public static VanillaGradleBytecodeProvider bytecodeFromJar() {
        return new VanillaGradleBytecodeProvider() {
            private final ConcurrentMap<String, FileSystem> files = new ConcurrentHashMap<>();

            @Override
            public void setBytecode(final String external, final String internal, final byte[] bytes) throws IOException {
                final Path result = this.zipFile(external).getPath(internal);
                Files.write(result, bytes);
            }

            @Override
            public void close() throws IOException {
                IOException error = null;
                for (final FileSystem file : this.files.values()) {
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
                final Path result = this.zipFile(external).getPath(internal);
                return Files.readAllBytes(result);
            }

            private FileSystem zipFile(final String external) throws IOException {
                try {
                    return this.files.computeIfAbsent(external, path -> {
                        final URI uri = URI.create("jar:" + new File(path).toURI());
                        try {
                            return FileSystems.getFileSystem(uri);
                        } catch (final FileSystemNotFoundException ex) {
                            try {
                                return FileSystems.newFileSystem(uri, Collections.emptyMap(), (ClassLoader) null);
                            } catch (final IOException ex2) {
                                throw new RuntimeException(ex2);
                            }
                        }
                    });
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

    public interface VanillaGradleBytecodeProvider extends IBytecodeProvider, AutoCloseable {

        void setBytecode(final String external, final String internal, final byte[] bytes) throws IOException;

        @Override
        void close() throws IOException;
    }
}