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
package org.spongepowered.gradle.vanilla.runs;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.process.CommandLineArgumentProvider;
import org.spongepowered.gradle.vanilla.internal.model.Argument;
import org.spongepowered.gradle.vanilla.internal.model.rule.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An argument provider that understands rules and meta tokens based on arguments from a manifest
 *
 * <p>Argument variables in the form {@code ${variable}} are interpreted using the environment tokens property.</p>
 */
final class ManifestDerivedArgumentProvider implements CommandLineArgumentProvider {
    private static final Pattern PARAMETER_TOKEN = Pattern.compile("\\$\\{([^}]+)}");

    private final MapProperty<String, String> environmentTokens;
    private final Provider<List<Argument>> arguments;
    private final RuleContext rules;

    ManifestDerivedArgumentProvider(
        final MapProperty<String, String> environmentTokens,
        final Provider<List<Argument>> arguments,
        final RuleContext rules
    ) {
        this.environmentTokens = environmentTokens;
        this.arguments = arguments;
        this.rules = rules;
    }

    @Override
    public Iterable<String> asArguments() {
        final List<String> outputArgs = new ArrayList<>();
        final StringBuilder builder = new StringBuilder();
        for (final Argument arg : this.arguments.get()) {
            if (arg.rules().test(this.rules)) {
                arg: for (final String argument : arg.value()) {
                    if (builder.length() > 0) {
                        builder.delete(0, builder.length());
                    }

                    final Matcher matcher = ManifestDerivedArgumentProvider.PARAMETER_TOKEN.matcher(argument);
                    while (matcher.find()) {
                        matcher.appendReplacement(builder, "");
                        final String value = this.environmentTokens.get().get(matcher.group(1));
                        if (value == null) {
                            // If we are the value to a command line argument and we're not present, delete the previous argument.
                            if (!outputArgs.isEmpty() && outputArgs.getLast().startsWith("-")) {
                                outputArgs.removeLast();
                            }
                            // Either way, skip this whole argument
                            continue arg;
                        }
                        builder.append(value);
                    }
                    matcher.appendTail(builder);
                    outputArgs.add(builder.toString());
                }
            }
        }
        return outputArgs;
    }
}
