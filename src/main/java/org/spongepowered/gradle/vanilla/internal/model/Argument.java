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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.immutables.value.Value;
import org.spongepowered.gradle.vanilla.internal.model.rule.RuleDeclaration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

@Value.Immutable
public interface Argument {

    static Argument of(final List<String> value) {
        return new ArgumentImpl(value, RuleDeclaration.empty());
    }

    static Argument of(final List<String> value, final RuleDeclaration rules) {
        return new ArgumentImpl(value, rules);
    }

    /**
     * The argument value.
     *
     * @return the value of the argument
     */
    @Value.Parameter
    List<String> value();

    @Value.Default
    @Value.Parameter
    default RuleDeclaration rules() {
        return RuleDeclaration.empty();
    }

    final class ArgumentTypeAdapter extends TypeAdapter<Argument> {
        private static final String VALUE = "value";
        private static final String RULES = "rules";
        private final TypeAdapter<RuleDeclaration> declaration;

        public ArgumentTypeAdapter(final TypeAdapter<RuleDeclaration> declaration) {
            this.declaration = declaration;
        }

        @Override
        public void write(final JsonWriter out, final Argument value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Argument read(final JsonReader in) throws IOException {
            switch (in.peek()) {
                case STRING: // literal argument
                    return new ArgumentImpl(Collections.singletonList(in.nextString()), RuleDeclaration.empty());
                case BEGIN_OBJECT: // argument with a rule
                    @Nullable List<String> value = null;
                    RuleDeclaration declaration = RuleDeclaration.empty();
                    in.beginObject();
                    while (in.peek() != JsonToken.END_OBJECT) {
                        final String key = in.nextName();
                        if (key.equals(ArgumentTypeAdapter.VALUE)) {
                            switch (in.peek()) {
                                case STRING:
                                    value = Collections.singletonList(in.nextString());
                                    break;
                                case BEGIN_ARRAY:
                                    in.beginArray();
                                    value = new ArrayList<>();
                                    while (in.peek() != JsonToken.END_ARRAY) {
                                        value.add(in.nextString());
                                    }
                                    in.endArray();
                                    break;
                                default:
                                    throw new JsonSyntaxException("Key " + key + " expected to be either a literal string or a list of strings");
                            }
                        } else if (key.equals(ArgumentTypeAdapter.RULES)) {
                            declaration = this.declaration.read(in);
                        }
                    }
                    in.endObject();
                    if (value == null) {
                        throw new JsonSyntaxException("Exited argument declaration without finding argument values");
                    }
                    return new ArgumentImpl(value, declaration);
                default:
                    throw new JsonSyntaxException("Expected either a literal argument or a rule, but got " + in.peek() + " at " + in.getPath());

            }
        }

        public static final class Factory implements TypeAdapterFactory {

            @Override
            @SuppressWarnings("unchecked")
            public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> type) {
                if (!type.getRawType().equals(Argument.class)) {
                    return null;
                }
                return (TypeAdapter<T>) new ArgumentTypeAdapter(gson.getAdapter(RuleDeclaration.class)).nullSafe();
            }
        }
    }
}
