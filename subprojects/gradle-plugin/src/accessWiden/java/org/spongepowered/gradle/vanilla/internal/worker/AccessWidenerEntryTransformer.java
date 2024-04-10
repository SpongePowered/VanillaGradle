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
import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
import net.neoforged.art.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class AccessWidenerEntryTransformer implements Transformer {
    private final AccessWidener widener;

    public AccessWidenerEntryTransformer(final AccessWidener widener) {
        this.widener = widener;
    }

    @Override
    public ClassEntry process(final ClassEntry entry) {
        // Because InnerClass attributes can be present in any class AW'd classes
        // are referenced from, we have to target every class to get a correct output.
        final ClassReader reader = new ClassReader(entry.getData());
        final ClassWriter writer = new ClassWriter(reader, 0);
        // TODO: Expose the ASM version constant somewhere visible to this worker
        final ClassVisitor visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, this.widener);
        reader.accept(visitor, 0);
        if (entry.isMultiRelease()) {
            return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray(), entry.getVersion());
        } else {
            return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
        }
    }
}
