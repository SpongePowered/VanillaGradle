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
package org.spongepowered.gradle.vanilla.task;

import org.cadixdev.atlas.Atlas;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.transformer.AtlasTransformCollection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@CacheableTask
public abstract class AtlasTransformTask extends DefaultTask implements ProcessedJarTask {

    private static final Lock ATLAS_EXECUTOR_LOCK = new ReentrantLock();
    private static volatile ExecutorService atlasExecutor;
    private static final ConcurrentHashMap<Path, Lock> REMAPPER_LOCKS = new ConcurrentHashMap<>();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    /**
     * Steps that will be applied to configure each Atlas instance before processing the input file
     *
     * @return the configuration steps.
     */
    @Nested
    public abstract AtlasTransformCollection getTransformations();

    public void transformations(final Action<AtlasTransformCollection> configure) {
        Objects.requireNonNull(configure, "configure").execute(this.getTransformations());
    }

    @TaskAction
    public void execute() throws IOException {
        final Path inputJar = this.getInputJar().get().getAsFile().toPath();

        ExecutorService executor = AtlasTransformTask.atlasExecutor;
        if (executor == null) {
            AtlasTransformTask.ATLAS_EXECUTOR_LOCK.lock();
            try {
                if (AtlasTransformTask.atlasExecutor == null) {
                    final int maxProcessors = Runtime.getRuntime().availableProcessors();
                    AtlasTransformTask.atlasExecutor = new ThreadPoolExecutor(maxProcessors / 4, maxProcessors,
                            30, TimeUnit.SECONDS,
                            new LinkedBlockingDeque<>());
                }
                executor = AtlasTransformTask.atlasExecutor;
            } finally {
                AtlasTransformTask.ATLAS_EXECUTOR_LOCK.unlock();
            }
        }


        final Lock remapLock = AtlasTransformTask.REMAPPER_LOCKS.computeIfAbsent(inputJar, path -> new ReentrantLock());
        remapLock.lock();
        try (final Atlas atlas = new Atlas(executor)) {
            for (final Action<? super Atlas> action : this.getTransformations().getTransformations().get()) {
                action.execute(atlas);
            }

            atlas.run(inputJar, this.getOutputJar().get().getAsFile().toPath());
        } finally {
            remapLock.unlock();
        }
    }

    @Override
    public RegularFileProperty outputJar() {
        return this.getOutputJar();
    }

    public static void registerExecutionCompleteListener(final Gradle gradle) {
        gradle.buildFinished(result -> {
            AtlasTransformTask.REMAPPER_LOCKS.clear();
            final ExecutorService exec = AtlasTransformTask.atlasExecutor;
            AtlasTransformTask.atlasExecutor = null;
            if (exec != null) {
                exec.shutdownNow();
            }
        });
    }

}
