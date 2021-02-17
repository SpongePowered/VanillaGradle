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
package org.spongepowered.gradle.vanilla.asm;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.spongepowered.gradle.vanilla.Constants;

public final class LocalVariableNamer extends MethodVisitor {

    private final boolean isStatic;
    private final int paramSlotCount;
    private int parameterCount = 0;

    public LocalVariableNamer(final boolean isStatic, final int paramSlotCount, final MethodVisitor methodVisitor) {
        super(Constants.ASM_VERSION, methodVisitor);
        this.isStatic = isStatic;
        this.paramSlotCount = paramSlotCount;
    }

    @Override
    public void visitParameter(final String name, final int access) {
        // Normalize parameter attributes
        super.visitParameter("param" + this.parameterCount++, access);
    }

    @Override
    public void visitLocalVariable(final String name, final String descriptor, final String signature, final Label start, final Label end,
            final int index) {
        final int paramSlotOffset = this.isStatic ? this.paramSlotCount : this.paramSlotCount + 1;
        final String varName;
        if (index == 0 && !this.isStatic) {
            varName = "this";
        } else if (index < paramSlotOffset) {
            varName = "param" + (index - (this.isStatic ? 0 : 1));
        } else {
            varName = "var" + (index - paramSlotOffset);
        }

        super.visitLocalVariable(varName, descriptor, signature, start, end, index);
    }
}