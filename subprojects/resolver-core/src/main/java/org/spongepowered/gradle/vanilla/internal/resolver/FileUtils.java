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
package org.spongepowered.gradle.vanilla.internal.resolver;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileUtils {

    private static final int MAX_TRIES = 2;

    private FileUtils() {
    }

    public static void atomicMove(final Path source, final Path destination) throws IOException {
        try {
            FileUtils.atomicMoveIfPossible(source, destination);
        } catch (final AccessDeniedException ex) {
            // Sometimes because of file locking this will fail... Let's just try again and hope for the best
            // Thanks Windows!
            for (int tries = 0; tries < FileUtils.MAX_TRIES; ++tries) {
                // Pause for a bit
                try {
                    Thread.sleep(10 * tries);
                    FileUtils.atomicMoveIfPossible(source, destination);
                    return;
                } catch (final AccessDeniedException ex2) {
                    if (tries == FileUtils.MAX_TRIES - 1) {
                        throw ex;
                    }
                } catch (final InterruptedException exInterrupt) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    private static void atomicMoveIfPossible(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void createDirectoriesSymlinkSafe(final Path directory) throws IOException {
        if (!Files.isDirectory(directory)) { // not checked properly by Files.createDirectories
            Files.createDirectories(directory);
        }
    }

    public static Path temporaryPath(final Path parent, final String key) throws IOException {
        return Files.createTempFile(parent, "." + key, "");
    }

    public static @Nullable BasicFileAttributes fileAttributesIfExists(final Path file) {
        try {
            return Files.getFileAttributeView(file, BasicFileAttributeView.class).readAttributes();
        } catch (final IOException ex) {
            return null;
        }
    }

}
