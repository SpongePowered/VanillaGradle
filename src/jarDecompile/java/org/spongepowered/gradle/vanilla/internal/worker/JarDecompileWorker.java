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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Decompile a Jar using MinecraftForge's fork of FernFlower "ForgeFlower"
 */
public abstract class JarDecompileWorker implements WorkAction<JarDecompileWorker.Parameters> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JarDecompileWorker.class);

    static final Map<String, Object> OPTIONS = new HashMap<>();
    private static final String TRUE = "1";
    private static final String FALSE = "0";

    static {
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, TRUE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, TRUE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.DECOMPILE_INNER, TRUE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH, TRUE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.REMOVE_BRIDGE, TRUE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.REMOVE_SYNTHETIC, TRUE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.LITERALS_AS_IS, FALSE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.UNIT_TEST_MODE, FALSE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.MAX_PROCESSING_METHOD, FALSE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.IGNORE_INVALID_BYTECODE, TRUE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, TRUE);
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.THREADS, Integer.toString(Runtime.getRuntime().availableProcessors() - 1));
        JarDecompileWorker.OPTIONS.put(IFernflowerPreferences.INDENT_STRING, "    " /* Constants.INDENT */);
    }

    public static abstract class Parameters implements WorkParameters {
        public abstract ConfigurableFileCollection getDecompileClasspath();
        public abstract RegularFileProperty getInputJar();
        public abstract RegularFileProperty getOutputJar();
        public abstract MapProperty<String, String> getExtraArgs();
    }

    @Override
    public void execute() {
        final Parameters params = this.getParameters();

        final Map<String, Object> ffArgs = new HashMap<>(params.getExtraArgs().get());
        for (final Map.Entry<String, Object> defaultArg : JarDecompileWorker.OPTIONS.entrySet()) {
            ffArgs.putIfAbsent(defaultArg.getKey(), defaultArg.getValue()); // don't override user-specified options
        }

        // Decompile
        final File input = params.getInputJar().get().getAsFile();
        try (final Decompilation.VanillaGradleBytecodeProvider bytecode = Decompilation.bytecodeFromJar()) {
            @SuppressWarnings("deprecation")
            final Fernflower decompiler = new Fernflower(
                bytecode,
                new LineMappingResultSaver(input.getAbsolutePath(), params.getOutputJar().get().getAsFile(), bytecode),
                ffArgs,
                new SLF4JFernFlowerLogger(JarDecompileWorker.LOGGER)
            );

            // add classes
            decompiler.addSource(input);

            for (final File library : params.getDecompileClasspath()) {
                decompiler.addLibrary(library);
            }

            // perform the decompile
            try {
                decompiler.decompileContext();
                JarDecompileWorker.LOGGER.warn("Successfully decompiled to {}", params.getOutputJar().get().getAsFile());
            } finally {
                decompiler.clearContext();
                System.gc();
            }
        } catch (final IOException ex) {
            JarDecompileWorker.LOGGER.error("Failed to decompile {}:", params.getInputJar().get().getAsFile(), ex);
        }
    }
}
