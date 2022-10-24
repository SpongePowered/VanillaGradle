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
package org.spongepowered.gradle.vanilla.renamer.spi;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import net.minecraftforge.fart.api.Transformer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.common.value.qual.MatchesRegex;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A service interface implemented to provide a {@link Transformer.Factory}.
 *
 * <p>Each implementation can provide exactly zero or one
 * {@link Transformer.Factory} instance.</p>
 *
 * <p>The {@link #create(OptionSet)} method may be called multiple times over
 * the lifetime of a provider instance. Implementations must
 * read options on every call.</p>
 *
 * @since 0.2.1
 */
public interface TransformerProvider {

    /**
     * The regex used to compile {@link #ID_PATTERN}, describing the valid
     * characters in provider IDs and argument names.
     *
     * @since 0.2.1
     */
    String ID_PATTERN_REGEX = "[a-z][a-z0-9-]*";

    /**
     * A pre-compiled pattern for the regex {@value #ID_PATTERN_REGEX}.
     *
     * @since 0.2.1
     */
    Pattern ID_PATTERN = Pattern.compile(TransformerProvider.ID_PATTERN_REGEX);

    /**
     * Provide a name.
     *
     * <p>This will be used to produce an option {@code --<id>}, which will
     * cause this transformer to be activated.</p>
     *
     * <p>Provider IDs must match the pattern {@value #ID_PATTERN_REGEX}.</p>
     *
     * @return the name used to identify this transformer provider
     * @since 0.2.1
     */
    @NonNull @MatchesRegex(TransformerProvider.ID_PATTERN_REGEX) String id();

    /**
     * A user-readable description of the transformer provided by this service implementation.
     *
     * <p>By default, this returns a generic message.</p>
     *
     * @return the description
     */
    default @NonNull String description() {
        return "Activate the '" + this.id() + "' transformer";
    }

    /**
     * Optional method to allow adding some sort of flag to the trigger option.
     *
     * @param orig the initial trigger option for this transformer
     * @return a decorated trigger option, if desired
     * @since 0.2.1
     */
    default OptionSpec<?> decorateTriggerOption(final @NonNull OptionSpecBuilder orig) {
        return orig;
    }

    /**
     * Register arguments that this transformer should respond to.
     *
     * <p>The provider instance should hold on to the returned
     * {@link OptionSpecBuilder} instances to read values from the parsed
     * option set.</p>
     *
     * @param parser the parser to register options with
     * @since 0.2.1
     */
    default void init(final @NonNull OptionConsumer parser) {}

    /**
     * Using the provided options, create a transformer factory.
     *
     * <p>This method will only be invoked if the transformer is requested.</p>
     *
     * @param options the options parsed from user input
     * @return a factory, if requested from the provided options, or {@code null}
     * @since 0.2.1
     */
    Transformer.Factory create(final @NonNull OptionSet options) throws TransformerProvisionException;

    /**
     * A consumer for namespaced parser options.
     *
     * @since 0.2.1
     */
    interface OptionConsumer {

        /**
         * Create an argument that has one single name.
         *
         * @param argument the argument label
         * @return a new option spec builder
         * @since 0.2.1
         */
        OptionSpecBuilder accepts(final @MatchesRegex(TransformerProvider.ID_PATTERN_REGEX) String argument);

        /**
         * Create an argument with a description.
         *
         * @param argument the argument label
         * @param description a description for the argument, shown in help output
         * @return a new option spec builder
         * @since 0.2.1
         */
        OptionSpecBuilder accepts(final @MatchesRegex(TransformerProvider.ID_PATTERN_REGEX) String argument, final String description);

        /**
         * Create an argument with several aliases.
         *
         * @param arguments the argument aliases
         * @return a new option spec builder
         * @since 0.2.1
         */
        OptionSpecBuilder accepts(final List<@MatchesRegex(TransformerProvider.ID_PATTERN_REGEX) String> arguments);

        /**
         * Create an argument with several aliases and a description.
         *
         * @param arguments the argument aliases
         * @param description a description for the argument, shown in help output
         * @return a new option spec builder
         * @since 0.2.1
         */
        OptionSpecBuilder accepts(final List<@MatchesRegex(TransformerProvider.ID_PATTERN_REGEX) String> arguments, final String description);

    }

}
