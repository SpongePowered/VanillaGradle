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
package org.spongepowered.gradle.vanilla.internal.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.gradle.vanilla.internal.Constants;

public final class LocalVariableNamingClassVisitor extends ClassVisitor {

    private final VariableScopeTracker tracker = new VariableScopeTracker();

    public LocalVariableNamingClassVisitor(final ClassVisitor classVisitor) {
        super(Constants.ASM_VERSION, classVisitor);
    }

    @Override
    public void visit(
        final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces
    ) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.tracker.className(name);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
        int paramVarCount = 0;
        try {
            final Type[] md = Type.getMethodType(descriptor).getArgumentTypes();
            paramVarCount = md.length;
        } catch (final Exception ignored) {
            // oh well, let's be tolerant of bad bytecode
        }
        return new LocalVariableNamer(
            (access & Opcodes.ACC_STATIC) != 0,
            paramVarCount,
            this.tracker,
            this.tracker.scope(name, descriptor),
            super.visitMethod(access, name, descriptor, signature, exceptions)
        );
    }
}
