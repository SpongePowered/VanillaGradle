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
package org.spongepowered.gradle.vanilla.internal.model;

import org.spongepowered.gradle.vanilla.internal.model.rule.RuleDeclaration;

import java.util.Collections;
import java.util.Map;

/**
 * @param downloads
 * @param name
 * @param natives A map of OS name to classifier.
 * <p>The value classifier may include the {@code ${arch}} token, which can
 * be either {@code 32} or {@code 64}.</p>
 * @param rules
 */
public record Library(LibraryDownloads downloads, GroupArtifactVersion name, Map<String, String> natives, RuleDeclaration rules) {

    @SuppressWarnings("ConstantValue")
    public Library {
        // Gson may still pass nulls
        if (natives == null) {
            natives = Collections.emptyMap();
        }
        if (rules == null) {
            rules = RuleDeclaration.empty();
        }
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
    public boolean isNatives() {
        return !this.natives().isEmpty();
    }
}
