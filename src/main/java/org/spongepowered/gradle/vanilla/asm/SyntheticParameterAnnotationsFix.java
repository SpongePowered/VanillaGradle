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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.spongepowered.gradle.vanilla.Constants;

public class SyntheticParameterAnnotationsFix extends ClassVisitor {

    private static final String CONSTRUCTOR = "<init>";

    public SyntheticParameterAnnotationsFix(final ClassVisitor parent) {
        super(Constants.ASM_VERSION, parent);
    }

    @Override
    public MethodVisitor visitMethod(
            final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
        final MethodVisitor parent = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (name.equals(CONSTRUCTOR)) {
            int expectedFormals = -1;
            if (signature != null && !signature.isEmpty()) {
                final ParameterCounting formalsCounter = new ParameterCounting();
                try {
                    new SignatureReader(signature).accept(formalsCounter);
                    expectedFormals = formalsCounter.count;
                } catch (final Exception ex) {
                    // There was an invalid signature, just strip the annotations
                }
            }

            return new MethodFixer(parent, expectedFormals);
        } else {
            return parent;
        }
    }

    static class MethodFixer extends MethodVisitor {

        private final int expectedFormals;
        private int actualParams;

        public MethodFixer(final MethodVisitor parent, final int expectedFormals) {
            super(Constants.ASM_VERSION, parent);
            this.expectedFormals = expectedFormals;
        }

        @Override
        public void visitAnnotableParameterCount(final int parameterCount, final boolean visible) {
            this.actualParams = parameterCount;
            if (this.expectedFormals >= 0) {
                super.visitAnnotableParameterCount(this.expectedFormals, visible);
            }
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, final String descriptor, final boolean visible) {
            if (this.expectedFormals >= 0) {
                // TODO: This assumes synthetic parameters are always at the beginning of constructors
                // This seems to be correct for current MC, but is not required so may turn out to be incorrect in the future.
                // Perhaps we can capture declared parameter types vs those in the method descriptor, and use those to align the parameters.
                if (this.actualParams > this.expectedFormals) {
                    parameter -= (this.actualParams - this.expectedFormals); // re-offset
                }
                return super.visitParameterAnnotation(parameter, descriptor, visible);
            }
            return null;
        }

    }

    static class ParameterCounting extends SignatureVisitor {

        int count;

        /**
         * Constructs a new {@link SignatureVisitor}.
         */
        public ParameterCounting() {
            super(Constants.ASM_VERSION);
        }

        @Override
        public SignatureVisitor visitParameterType() {
            this.count++;
            return super.visitParameterType();
        }
    }

}
