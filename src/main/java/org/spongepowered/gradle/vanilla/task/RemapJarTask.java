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
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.commons.ClassRemapper;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.asm.LocalVariableNamingClassVisitor;
import org.spongepowered.gradle.vanilla.transformer.SignatureStripperTransformer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class RemapJarTask extends DefaultTask {

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
        final Atlas atlas = new Atlas();

        final MappingSet scratchMappings = MappingSet.create();
        try (final BufferedReader reader = Files.newBufferedReader(this.getMappingsFile().getAsFile().get().toPath(), StandardCharsets.UTF_8)) {
            final ProGuardReader proguard = new ProGuardReader(reader);
            proguard.read(scratchMappings);
        }

        final MappingSet mappings = scratchMappings.reverse();

        atlas.install(ctx -> SignatureStripperTransformer.INSTANCE);
        atlas.install(ctx -> new JarEntryRemappingTransformer(new LorenzRemapper(mappings, ctx.inheritanceProvider()), (parent, mapper) ->
            new ClassRemapper(new LocalVariableNamingClassVisitor(parent), mapper)));

        atlas.run(this.getInputJar().get().getAsFile().toPath(), this.getOutputJar().get().getAsFile().toPath());
    }
}
