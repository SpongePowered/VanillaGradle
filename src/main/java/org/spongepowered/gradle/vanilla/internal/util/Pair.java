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
package org.spongepowered.gradle.vanilla.internal.util;

import java.util.Map;
import java.util.Objects;

/**
 * Represent an immutable pair of values.
 *
 * @param <A> the first value type
 * @param <B> the second value type
 * @param first the first value
 * @param second the second value
 */
public record Pair<A, B>(A first, B second) {

    /**
     * Create a new {@link Pair} from an existing map entry.
     *
     * @param entry an entry to convert to a {@link Pair}
     * @param <U> key type
     * @param <V> value type
     * @return a new {@link Pair}
     */
    public static <U, V> Pair<U, V> fromEntry(final Map.Entry<U, V> entry) {
        Objects.requireNonNull(entry, "entry");
        return new Pair<>(entry.getKey(), entry.getValue());
    }

}
