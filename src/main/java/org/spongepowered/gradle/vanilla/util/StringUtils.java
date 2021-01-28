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
package org.spongepowered.gradle.vanilla.util;

import org.gradle.process.CommandLineArgumentProvider;

public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Capitalize the first character of a single word.
     *
     * @param input text to transform
     * @return capitalized word
     */
    public static String capitalize(final String input) {
        if (input.isEmpty()) {
            return input;
        }

        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    public static String join(final Iterable<CommandLineArgumentProvider> providers, final boolean quoted) {
        final StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (final CommandLineArgumentProvider provider : providers) {
            for (final String argument : provider.asArguments()) {
                if (!first) {
                    builder.append(" ");
                }
                first = false;
                if (quoted) {
                    builder.append("\"").append(argument).append("\"");
                } else {
                    builder.append(argument);
                }
            }
        }
        return builder.toString();
    }

}
