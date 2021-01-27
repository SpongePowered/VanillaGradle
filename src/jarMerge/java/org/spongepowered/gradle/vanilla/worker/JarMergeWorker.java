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

import net.minecraftforge.mergetool.AnnotationVersion;
import net.minecraftforge.mergetool.Merger;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Combine a client and server jar together
 */
public abstract class JarMergeWorker implements WorkAction<JarMergeWorker.Parameters> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JarMergeWorker.class);

    public static abstract class Parameters implements WorkParameters {
        public abstract RegularFileProperty getClientJar();
        public abstract RegularFileProperty getServerJar();
        public abstract RegularFileProperty getMergedJar();
    }

    @Override
    public void execute() {
        final Parameters params = this.getParameters();
        final Merger merger = new Merger(
            params.getClientJar().get().getAsFile(),
            params.getServerJar().get().getAsFile(),
            params.getMergedJar().get().getAsFile()
        );
        merger.annotate(AnnotationVersion.API, true);
        merger.keepData();

        try {
            merger.process();
        } catch (final IOException ex) {
            JarMergeWorker.LOGGER.error("Failed to merge jars", ex);
            throw new RuntimeException("Failed to merge jars", ex);
        }
    }
}
