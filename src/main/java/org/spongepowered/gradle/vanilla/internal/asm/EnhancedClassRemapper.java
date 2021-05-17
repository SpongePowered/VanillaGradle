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
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.gradle.vanilla.internal.Constants;

/**
 * Extend ASM's ClassRemapper with a few things:
 */
public class EnhancedClassRemapper extends ClassRemapper {

    private static final String SOURCE_FILE_OBF = "SourceFile";

    private String mappedClassName;

    public EnhancedClassRemapper(final ClassVisitor classVisitor, final Remapper remapper) {
        super(Constants.ASM_VERSION, classVisitor, remapper);
    }

    @Override
    public void visit(
        final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces
    ) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.mappedClassName = this.remapper.mapType(name);
    }

    @Override
    public void visitSource(final String source, final String debug) {
        if (source.equals(EnhancedClassRemapper.SOURCE_FILE_OBF)) {
            // We will make a best guess at what the SourceFile should be, by taking the simple name of the class and stripping any potential inner classes.
            // This can be wrong, since a single source file can contain multiple top-level classes, but this is the best we can guess.
            if (this.mappedClassName == null) {
                throw new IllegalStateException("Class name was not found");
            }
            final String mappedClass = this.mappedClassName;
            final int lastPackageElement = mappedClass.lastIndexOf('/') + 1;
            final int innerClassElement = mappedClass.indexOf('$', lastPackageElement); // TODO: Handle classes with a $ prefix
            super.visitSource(mappedClass.substring(
                lastPackageElement, // after last `/`
                Math.min(mappedClass.length(), innerClassElement == -1 ? Integer.MAX_VALUE : innerClassElement) // but before first $
            ) + ".java", debug);
            return;
        }

        // TODO: attempt to map this?
        super.visitSource(source, debug);
    }
}
