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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RuleDeclarationTypeAdapter extends TypeAdapter<RuleDeclaration> {

    private static final String ACTION = "action";
    private final Map<String, Rule<?>> ruleTypes;
    private final Gson gson;

    RuleDeclarationTypeAdapter(final Gson gson, final Map<String, Rule<?>> ruleTypes) {
        this.gson = gson;
        this.ruleTypes = ruleTypes;
    }

    @Override
    public void write(final JsonWriter out, final RuleDeclaration value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RuleDeclaration read(final JsonReader in) throws IOException {
        if (in.peek() != JsonToken.BEGIN_ARRAY) {
            throw new JsonSyntaxException("Expected rule declaration at " + in.getPath() + " to be an array");
        }
        final RuleDeclaration.Builder builder = RuleDeclaration.builder();
        in.beginArray();
        // read rules
        while (in.peek() != JsonToken.END_ARRAY) {
            in.beginObject();
            while (in.peek() != JsonToken.END_OBJECT) {
                // each rule has 1-2 sections:
                final String key = in.nextName();
                if (key.equals(ACTION)) {
                    // rule action
                    builder.action(this.gson.fromJson(in, RuleAction.class));
                } else {
                    // zero or more rule values
                    final Rule<?> expected = this.ruleTypes.get(key);
                    if (expected == null) {
                        throw new IOException("Unknown rule type '" + key + "' at " + in.getPath());
                    }
                    final Object value = this.gson.fromJson(in, expected.type().getType());
                    builder.rule((Rule<Object>) expected, value);
                }
            }
            in.endObject();
            builder.nextEntry();
        }
        in.endArray();
        return builder.build();
    }

    public static final class Factory implements TypeAdapterFactory {
        private final Map<String, Rule<?>> ruleTypes;

        public Factory(final Rule<?>... rules) {
            this.ruleTypes = new HashMap<>(rules.length);
            for (final Rule<?> rule : rules) {
                this.ruleTypes.put(rule.id(), rule);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> type) {
            if (!type.getRawType().equals(RuleDeclaration.class)) {
                return null;
            }
            return (TypeAdapter<T>) new RuleDeclarationTypeAdapter(gson, this.ruleTypes);
        }
    }
}
