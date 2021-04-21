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
package org.spongepowered.gradle.vanilla.model.rule;

import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A rule that matches feature flags defined in the rule context.
 */
public final class FeatureRule implements Rule<Map<String, Boolean>> {

    public static final FeatureRule INSTANCE = new FeatureRule();
    private static final String CTX_FEATURES = "vanillagradle:features";
    private static final TypeToken<Map<String, Boolean>> TYPE = new TypeToken<Map<String, Boolean>>() {};

    /**
     * Known feature flags.
     */
    public static final class Features {

        /**
         * Whether a user is in demo mode
         */
        public static final String IS_DEMO_USER = "is_demo_user";

        /**
         * Whether a user has specific window size set.
         */
        public static final String HAS_CUSTOM_RESOLUTION = "has_custom_resolution";
    }

    private FeatureRule() {
    }

    /**
     * Set a feature.
     *
     * @param context the rule context that will eventually be tested
     * @param feature a feature flag
     * @param value the value for the feature
     * @see Features for a selection of known features
     */
    public static void setFeature(final RuleContext context, final String feature, final boolean value) {
        context.computeIfAbsent(FeatureRule.CTX_FEATURES, $ -> new HashMap<String, Boolean>()).put(feature, value);
    }

    @Override
    public String id() {
        return "features";
    }

    @Override
    public TypeToken<Map<String, Boolean>> type() {
        return FeatureRule.TYPE;
    }

    @Override
    public boolean test(final RuleContext context, final Map<String, Boolean> value) {
        final Map<String, Boolean> result = context.<Map<String, Boolean>>get(FeatureRule.CTX_FEATURES).orElse(null);
        if (result == null || result.isEmpty()) {
            return value.isEmpty();
        }

        for (final Map.Entry<String, Boolean> entry : value.entrySet()) {
            if (!Objects.equals(result.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

}
