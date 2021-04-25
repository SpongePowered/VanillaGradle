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
package org.spongepowered.gradle.vanilla.model;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.spongepowered.gradle.vanilla.model.rule.RuleDeclaration;

import java.util.Collections;
import java.util.Map;

@Value.Immutable
@Gson.TypeAdapters
public interface Library {

    LibraryDownloads downloads();

    GroupArtifactVersion name();

    /**
     * A map of OS name to classifier.
     *
     * <p>The value classifier may include the {@code ${arch}} token, which can
     * be either {@code 32} or {@code 64}.</p>
     *
     * @return the natives this artifact may have
     */
    @Value.Default
    default Map<String, String> natives() {
        return Collections.emptyMap();
    }

    /**
     * Get whether this is a natives declaration or a standard dependency declaration.
     *
     * <p>When a dependency contains natives, Mojang specifies it twice: Once
     * with natives, and once without. The ones with natives are used only for
     * extracting natives, and the ones without are only used when
     * downloading dependencies.</p>
     *
     * @return whether this is a natives dependency
     */
    default boolean isNatives() {
        return !this.natives().isEmpty();
    }

    @Value.Default
    default RuleDeclaration rules() {
        return RuleDeclaration.empty();
    }
}
