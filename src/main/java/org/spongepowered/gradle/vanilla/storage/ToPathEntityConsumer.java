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

import static java.util.Objects.requireNonNull;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An entity consumer that will write the provided data to a file
 */
final class ToPathEntityConsumer extends AbstractBinAsyncEntityConsumer<Path> {
    private static final int CHUNK_SIZE = 8192;
    private static final int MAX_TRIES = 2;

    private final Path target;
    private Path unwrappedTarget;
    private Path targetTemp;
    private @Nullable FileChannel output;

    public ToPathEntityConsumer(final Path target) {
        this.target = target;
    }

    private static Path temporaryPath(final Path parent, final String key) {
        final String fileName = "." + System.nanoTime() + ThreadLocalRandom.current().nextInt()
            + requireNonNull(key, "key").replaceAll("[\\\\/:]", "-") + ".tmp";
        return parent.resolve(fileName);
    }

    @Override
    protected void streamStart(final ContentType contentType) throws IOException {
        if (this.output != null)  {
            throw new IOException("Tried to begin a stream while one was already in progress!");
        }
        Path unwrappedTarget = this.target.toAbsolutePath();

        // unwrap any symbolic links
        try {
            while (Files.isSymbolicLink(unwrappedTarget)) {
                unwrappedTarget = Files.readSymbolicLink(unwrappedTarget);
            }
        } catch (final UnsupportedOperationException | IOException ex) {
            // ignore
        }
        this.unwrappedTarget = unwrappedTarget;

        this.targetTemp = ToPathEntityConsumer.temporaryPath(unwrappedTarget.getParent(), unwrappedTarget.getFileName().toString());
        this.output = FileChannel.open(this.targetTemp, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    protected Path generateContent() {
        if (this.output != null) {
            throw new IllegalStateException("Tried to get written path before output has been closed");
        }
        return this.target;
    }

    @Override
    protected int capacityIncrement() {
        return ToPathEntityConsumer.CHUNK_SIZE;
    }

    @Override
    protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException {
        if (this.output == null) {
            throw new IOException("Tried to provide data before stream start or after stream end");
        }

        this.output.write(src);
        if (endOfStream) { // no more data coming
            // Since we've successfully completed, we can safely move the result over to the proper destination
            this.output.force(true);
            this.output.close();
            try {
                Files.move(this.targetTemp, this.unwrappedTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AccessDeniedException ex) {
                // Sometimes because of file locking this will fail... Let's just try again and hope for the best
                // Thanks Windows!
                for (int tries = 0; tries < ToPathEntityConsumer.MAX_TRIES; ++tries) {
                    // Pause for a bit
                    try {
                        Thread.sleep(5 * tries);
                        Files.move(this.targetTemp, this.unwrappedTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (final AccessDeniedException ex2) {
                        if (tries == ToPathEntityConsumer.MAX_TRIES - 1) {
                            throw ex;
                        }
                    } catch (final InterruptedException exInterrupt) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            } finally {
                this.output = null;
                this.targetTemp = null;
                this.unwrappedTarget = null;
            }
        }
    }

    @Override
    public void releaseResources() {
        if (this.output != null) {
            try {
                this.output.close();
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            } finally {
                this.output = null;
            }
        }
    }
}
