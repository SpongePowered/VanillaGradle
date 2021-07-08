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
package org.spongepowered.gradle.vanilla.internal.model.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A testable declaration of rules with their values.
 */
public final class RuleDeclaration {
    private static final RuleDeclaration EMPTY = new RuleDeclaration(Collections.emptyList());

    private final List<Entry> entries;

    RuleDeclaration(final List<Entry> entries) {
        this.entries = entries;
    }

    public static RuleDeclaration empty() {
        return RuleDeclaration.EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    static final class Entry {
        final RuleAction action;
        final Map<Rule<?>, Object> rules;

        Entry(final RuleAction action, final Map<Rule<?>, Object> rules) {
            this.action = action;
            this.rules = rules;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean test(final RuleContext ctx) {
        if (this.entries.isEmpty()) {
            return true;
        }

        RuleAction action = RuleAction.DENY;
        for (final Entry entry : this.entries) {
            boolean matches = true;
            for (final Map.Entry<Rule<?>, Object> rule : entry.rules.entrySet()) {
                if (!((Rule<Object>) rule.getKey()).test(ctx, rule.getValue())) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                action = entry.action;
            }
        }
        return action != RuleAction.DENY;
    }


    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();
        private RuleAction action = RuleAction.ALLOW;
        private Map<Rule<?>, Object> rules = new HashMap<>();

        public Builder action(final RuleAction action) {
            this.action = action;
            return this;
        }

        public <T> Builder rule(final Rule<T> rule, final T value) {
            this.rules.put(rule, value);
            return this;
        }

        public Builder nextEntry() {
            this.entries.add(new Entry(this.action, Collections.unmodifiableMap(new HashMap<>(this.rules))));
            this.action = RuleAction.ALLOW;
            this.rules.clear();
            return this;
        }

        public RuleDeclaration build() {
            return new RuleDeclaration(new ArrayList<>(this.entries));
        }
    }


}
