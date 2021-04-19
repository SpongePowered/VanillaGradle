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
package org.spongepowered.gradle.vanilla.transformer;

import org.cadixdev.bombe.analysis.InheritanceProvider;
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.spongepowered.gradle.vanilla.asm.EnhancedClassRemapper;
import org.spongepowered.gradle.vanilla.asm.LocalVariableNamingClassVisitor;
import org.spongepowered.gradle.vanilla.asm.SyntheticParameterAnnotationsFix;

import java.util.Set;

public final class AtlasTransformers {

    private AtlasTransformers() {
    }

    public static JarEntryTransformer filterEntries(final Set<String> allowedPackages) {
        return new FilterClassesTransformer(allowedPackages);
    }

    public static JarEntryTransformer stripSignatures() {
        return SignatureStripperTransformer.INSTANCE;
    }

    public static JarEntryTransformer remap(final MappingSet mappings, final InheritanceProvider inheritanceProvider) {
        return new JarEntryRemappingTransformer(new LorenzRemapper(mappings, inheritanceProvider), (parent, mapper) ->
            new EnhancedClassRemapper(new SyntheticParameterAnnotationsFix(new LocalVariableNamingClassVisitor(parent)), mapper));
    }

}
