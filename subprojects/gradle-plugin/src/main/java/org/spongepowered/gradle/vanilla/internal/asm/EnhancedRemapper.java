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

import org.cadixdev.bombe.analysis.InheritanceProvider;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

public class EnhancedRemapper extends LorenzRemapper {

    public EnhancedRemapper(final MappingSet mappings, final InheritanceProvider inheritanceProvider) {
        super(mappings, inheritanceProvider);
    }

    @Override
    public Object mapValue(final Object value) {
        if (value instanceof Handle) {
            // Backport of ASM!327 https://gitlab.ow2.org/asm/asm/-/merge_requests/327
            final Handle handle = (Handle) value;
            final boolean isFieldHandle = handle.getTag() <= Opcodes.H_PUTSTATIC;

            return new Handle(
                handle.getTag(),
                this.mapType(handle.getOwner()),
                isFieldHandle
                ? this.mapFieldName(handle.getOwner(), handle.getName(), handle.getDesc())
                : this.mapMethodName(handle.getOwner(), handle.getName(), handle.getDesc()),
                isFieldHandle ? this.mapDesc(handle.getDesc()) : this.mapMethodDesc(handle.getDesc()),
                handle.isInterface());
        } else {
            return super.mapValue(value);
        }
    }
}
