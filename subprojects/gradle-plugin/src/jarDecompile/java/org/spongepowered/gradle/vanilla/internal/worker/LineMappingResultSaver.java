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

import org.jetbrains.java.decompiler.main.decompiler.SingleFileSaver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class LineMappingResultSaver extends SingleFileSaver {

    private static final Logger LOGGER = LoggerFactory.getLogger(LineMappingResultSaver.class);

    private final String source;
    final Decompilation.VanillaGradleBytecodeProvider bytecodeProvider;

    public LineMappingResultSaver(final String source, final File target, final Decompilation.VanillaGradleBytecodeProvider bytecodeProvider) {
        super(target);
        this.source = source;
        this.bytecodeProvider = bytecodeProvider;
    }

    @Override
    public void saveClassEntry(
        final String path, final String archiveName, final String qualifiedName, final String entryName, final String content, final int[] mapping
    ) {
        super.saveClassEntry(path, archiveName, qualifiedName, entryName, content, mapping);
        if (mapping != null) {
            // Get the input archive name
            try {
                final String inputName = qualifiedName + ".class";
                final byte[] clazz = this.bytecodeProvider.getBytecode(this.source, inputName);
                final ClassReader reader = new ClassReader(clazz);
                final ClassWriter output = new ClassWriter(reader, 0);
                reader.accept(new LineMappingVisitor(output, mapping), 0);
                // Find the entry, then modify it? if possible?
                this.bytecodeProvider.setBytecode(this.source, inputName, output.toByteArray());
            } catch (final IOException ex) {
                LineMappingResultSaver.LOGGER.warn("Line mapping failed on {} in {}", entryName, archiveName, ex);
            }
        }
    }

}
