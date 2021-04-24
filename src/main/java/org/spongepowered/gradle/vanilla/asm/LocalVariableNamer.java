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

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.spongepowered.gradle.vanilla.Constants;

public final class LocalVariableNamer extends MethodVisitor {

    private final boolean isStatic;
    private final int paramTotal;
    private int parameterCount = 0;
    private int lvtCount;
    private final VariableScopeTracker scopeTracker;
    private final VariableScope scope;

    public LocalVariableNamer(
        final boolean isStatic,
        final int paramTotal,
        final VariableScopeTracker scopeTracker,
        final VariableScope scope,
        final MethodVisitor methodVisitor
    ) {
        super(Constants.ASM_VERSION, methodVisitor);
        this.isStatic = isStatic;
        this.paramTotal = paramTotal;
        this.scopeTracker = scopeTracker;
        this.scope = scope;
    }

    @Override
    public void visitParameter(final String name, final int access) {
        // Normalize parameter attributes
        if (LocalVariableNamer.isValidJavaIdentifier(name)) {
            super.visitParameter(this.scope.produceSafe(name, VariableScope.Usage.PARAMETER), access);
        } else {
            super.visitParameter(this.scope.produceSafe("param" + this.parameterCount, VariableScope.Usage.PARAMETER), access);
        }

        this.parameterCount++;
    }

    @Override
    public void visitInvokeDynamicInsn(
        final String name, final String descriptor, final Handle bootstrapMethodHandle, final Object... bootstrapMethodArguments
    ) {
        for (final Object arg : bootstrapMethodArguments) {
            if (arg instanceof Handle) {
                final Handle referenced = (Handle) arg;
                if (referenced.getOwner().equals(this.scopeTracker.className())) { // a reference to a method within ourselves, assume nested scope
                    this.scopeTracker.makeChild(this.scope, referenced.getName(), referenced.getDesc());
                }
            }
        }
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }

    @Override
    public void visitLocalVariable(final String name, final String descriptor, final String signature, final Label start, final Label end,
            final int index) {
        final int varNumber = this.lvtCount++;
        final int paramLocalVarSplit = this.isStatic ? this.paramTotal : this.paramTotal + 1;
        final String varName;
        if (LocalVariableNamer.isValidJavaIdentifier(name)) {
            varName = name;
        } else if (varNumber == 0 && !this.isStatic) {
            varName = "this";
        } else if (varNumber < paramLocalVarSplit) {
            varName = "param" + (varNumber - (this.isStatic ? 0 : 1));
        } else {
            varName = "var" + (varNumber - paramLocalVarSplit);
        }

        super.visitLocalVariable(this.scope.produceSafe(varName, VariableScope.Usage.LVT), descriptor, signature, start, end, index);
    }

    private static boolean isValidJavaIdentifier(final String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }

        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }

        return true;
    }
}