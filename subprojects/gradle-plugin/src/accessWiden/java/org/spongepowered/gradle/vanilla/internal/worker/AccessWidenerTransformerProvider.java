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
package org.spongepowered.gradle.vanilla.internal.worker;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.minecraftforge.fart.api.Transformer;
import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Function;

public final class AccessWidenerTransformerProvider implements Function<Set<Path>, Transformer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessWidenerTransformerProvider.class);

    @Override
    public Transformer apply(final Set<Path> paths) {
        final AccessWidener widener = new AccessWidener();
        final AccessWidenerReader reader = new AccessWidenerReader(widener);

        for (final Path widenerFile : paths) {
            try (final BufferedReader fileReader = Files.newBufferedReader(widenerFile, StandardCharsets.UTF_8)) {
                reader.read(fileReader);
            } catch (final IOException ex) {
                AccessWidenerTransformerProvider.LOGGER.error("Failed to read access widener from file {}", widenerFile, ex);
                throw new GradleException("Access widening failed");
            }
        }

        return new AccessWidenerEntryTransformer(widener);
    }
}
