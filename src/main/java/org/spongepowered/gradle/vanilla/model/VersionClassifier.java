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

import com.google.gson.annotations.SerializedName;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Known types of versions published by mojang.
 */
public enum VersionClassifier {
    @SerializedName("snapshot")
    SNAPSHOT,
    @SerializedName("old_alpha")
    OLD_ALPHA,
    @SerializedName("old_beta")
    OLD_BETA,
    /**
     * Used for out-of-band releases.
     */
    @SerializedName("pending")
    PENDING,
    @SerializedName("release")
    RELEASE;

    private static final List<String> NAMES;
    private static final Map<String, VersionClassifier> BY_NAME;

    private final String id;

    VersionClassifier() {
        this.id = this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * @return an unmodifiable list of known version classifier IDs.
     */
    public static List<String> ids() {
        return VersionClassifier.NAMES;
    }

    /**
     * Look up a classifier by case-insensitive ID.
     *
     * @param id case-insensitive ID
     * @return a version classifier, or null if unknown
     */
    public static @Nullable VersionClassifier byId(final String id) {
        return VersionClassifier.BY_NAME.get(id.toLowerCase(Locale.ROOT));
    }

    public String id() {
        return this.id;
    }

    static {
        final VersionClassifier[] classifiers = VersionClassifier.values();
        final List<String> values = new ArrayList<>(classifiers.length);
        final Map<String, VersionClassifier> mapping = new HashMap<>(classifiers.length);
        for (final VersionClassifier classifier : classifiers) {
            values.add(classifier.id);
            mapping.put(classifier.id, classifier);
        }
        NAMES = Collections.unmodifiableList(values);
        BY_NAME = Collections.unmodifiableMap(mapping);
    }
}
