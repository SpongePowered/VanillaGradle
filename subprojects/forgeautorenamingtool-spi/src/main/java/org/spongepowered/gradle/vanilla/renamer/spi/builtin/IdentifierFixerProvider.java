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
package org.spongepowered.gradle.vanilla.renamer.spi.builtin;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import net.minecraftforge.fart.api.IdentifierFixerConfig;
import net.minecraftforge.fart.api.Transformer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.immutables.metainf.Metainf;
import org.spongepowered.gradle.vanilla.renamer.spi.TransformerProvider;

@Metainf.Service
public final class IdentifierFixerProvider implements TransformerProvider {

    private static final String ID = "ids-fix";

    private @MonotonicNonNull OptionSpec<IdentifierFixerConfig> triggerOption;

    @Override
    public @NonNull String id() {
        return IdentifierFixerProvider.ID;
    }

    @Override
    public OptionSpec<?> decorateTriggerOption(final @NonNull OptionSpecBuilder orig) {
        return this.triggerOption = orig.withOptionalArg()
            .withValuesConvertedBy(new EnumConverter<>(IdentifierFixerConfig.class))
            .defaultsTo(IdentifierFixerConfig.ALL);
    }

    @Override
    public Transformer.Factory create(final @NonNull OptionSet options) {
        return Transformer.identifierFixerFactory(options.valueOf(this.triggerOption));
    }
}
