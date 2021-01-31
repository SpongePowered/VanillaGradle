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
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.commons.ClassRemapper;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.asm.LocalVariableNamingClassVisitor;
import org.spongepowered.gradle.vanilla.asm.SyntheticParameterAnnotationsFix;
import org.spongepowered.gradle.vanilla.transformer.SignatureStripperTransformer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class RemapJarTask extends DefaultTask implements ProcessedJarTask {

    private static final Lock ATLAS_EXECUTOR_LOCK = new ReentrantLock();
    private static volatile ExecutorService atlasExecutor = null;
    private static final ConcurrentHashMap<Path, Lock> REMAPPER_LOCKS = new ConcurrentHashMap<>();

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @InputFile
    public abstract RegularFileProperty getMappingsFile();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    public RemapJarTask() {
        this.setGroup(Constants.TASK_GROUP);
    }
    
    @TaskAction
    public void execute() throws IOException {
        final Path inputJar = this.getInputJar().get().getAsFile().toPath();

        final MappingSet scratchMappings = MappingSet.create();
        try (final BufferedReader reader = Files.newBufferedReader(this.getMappingsFile().getAsFile().get().toPath(), StandardCharsets.UTF_8)) {
            final ProGuardReader proguard = new ProGuardReader(reader);
            proguard.read(scratchMappings);
        }

        final MappingSet mappings = scratchMappings.reverse();

        ExecutorService executor = RemapJarTask.atlasExecutor;
        if (executor == null) {
            RemapJarTask.ATLAS_EXECUTOR_LOCK.lock();
            try {
                if (RemapJarTask.atlasExecutor == null) {
                    final int maxProcessors = Runtime.getRuntime().availableProcessors();
                    RemapJarTask.atlasExecutor = new ThreadPoolExecutor(maxProcessors / 4, maxProcessors,
                            30, TimeUnit.SECONDS,
                            new LinkedBlockingDeque<>());
                }
                executor = RemapJarTask.atlasExecutor;
            } finally {
                RemapJarTask.ATLAS_EXECUTOR_LOCK.unlock();
            }
        }


        final Lock remapLock = RemapJarTask.REMAPPER_LOCKS.computeIfAbsent(inputJar, path -> new ReentrantLock());
        remapLock.lock();
        final Atlas atlas = new Atlas(executor);
        try {
            atlas.install(ctx -> SignatureStripperTransformer.INSTANCE);
            atlas.install(ctx -> new JarEntryRemappingTransformer(new LorenzRemapper(mappings, ctx.inheritanceProvider()), (parent, mapper) ->
                    new ClassRemapper(new SyntheticParameterAnnotationsFix(new LocalVariableNamingClassVisitor(parent)), mapper)));

            atlas.run(inputJar, this.getOutputJar().get().getAsFile().toPath());
        } finally {
            atlas.close();
            remapLock.unlock();
        }
    }

    @Override
    public RegularFileProperty outputJar() {
        return this.getOutputJar();
    }

    public static void registerExecutionCompleteListener(final Gradle gradle) {
        gradle.buildFinished(result -> {
            RemapJarTask.REMAPPER_LOCKS.clear();
            final ExecutorService exec = RemapJarTask.atlasExecutor;
            RemapJarTask.atlasExecutor = null;
            if (exec != null) {
                exec.shutdownNow();
            }
        });
    }
}
