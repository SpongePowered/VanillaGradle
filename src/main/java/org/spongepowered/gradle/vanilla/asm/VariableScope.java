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

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Rudimentary scope model to prevent var name collisions between lambda methods and their containing methods
 */
final class VariableScope {

    private static final String THIS = "this";

    private final Map<String, UsageHolder> usedNames = new HashMap<>();
    private final @Nullable VariableScope parent;

    static VariableScope root() {
        return new VariableScope(null);
    }

    VariableScope(final @Nullable VariableScope parent) {
        this.parent = parent;
    }

    // Scope-by-scope for ourselves, but don't care for parents
    boolean used(final String variable, final Usage usage) {
        if (usage == Usage.LVT && variable.equals(VariableScope.THIS)) {
            return false;
        } else if (this.usedNames.getOrDefault(variable, UsageHolder.NONE).has(usage)) {
            return true;
        } else if (this.parent == null) {
            return false;
        }

        VariableScope scope = this.parent;

        while (!scope.usedNames.containsKey(variable)) {
            if (scope.parent == null) {
                return false;
            }
            scope = scope.parent;
        }

        return true;
    }

    String produceSafe(final String variable, final Usage usage) {
       if (!this.used(variable, usage)) {
           this.usedNames.compute(variable, (k, existing) -> (existing == null ? UsageHolder.NONE : existing).plus(usage));
           return variable;
       }

       final StringBuilder resultBuilder = new StringBuilder(variable.length() + 1);
       resultBuilder.append(variable);
       String result;
       do {
           resultBuilder.append("x");
           result = resultBuilder.toString();
       } while (this.used(result, usage));

        this.usedNames.compute(variable, (k, existing) -> (existing == null ? UsageHolder.NONE : existing).plus(usage));
       return result;
    }

    static class UsageHolder {

        static final UsageHolder NONE = new UsageHolder(false, false);
        static final UsageHolder PARAM = new UsageHolder(false, true);
        static final UsageHolder LVT = new UsageHolder(true, false);
        static final UsageHolder BOTH = new UsageHolder(true, true);

        final boolean lvt;
        final boolean param;

        private UsageHolder(final boolean lvt, final boolean param) {
            this.param = param;
            this.lvt = lvt;
        }

        UsageHolder plus(final Usage usage) {
            switch (usage) {
                case LVT: return this.param ? UsageHolder.BOTH : UsageHolder.LVT;
                case PARAMETER: return this.lvt ? UsageHolder.BOTH : UsageHolder.PARAM;
            }
            return this;
        }

        boolean any() {
            return this.lvt || this.param;
        }

        boolean has(final Usage usage) {
            switch (usage) {
                case LVT: return this.lvt;
                case PARAMETER: return this.param;
                default: throw new IllegalArgumentException("Unknown " + usage);
            }
        }
    }

    enum Usage {
        LVT,
        PARAMETER,
    }

}
