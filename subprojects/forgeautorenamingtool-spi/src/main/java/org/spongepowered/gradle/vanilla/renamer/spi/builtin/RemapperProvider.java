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
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.immutables.metainf.Metainf;
import org.spongepowered.gradle.vanilla.renamer.spi.TransformerProvider;
import org.spongepowered.gradle.vanilla.renamer.spi.TransformerProvisionException;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Metainf.Service
public final class RemapperProvider implements TransformerProvider {

    private static final String ID = "map";

    private @MonotonicNonNull OptionSpec<File> triggerOption;
    private @MonotonicNonNull OptionSpec<Void> reverseO;

    @Override
    public @NonNull String id() {
        return RemapperProvider.ID;
    }

    @Override
    public OptionSpec<?> decorateTriggerOption(final @NonNull OptionSpecBuilder orig) {
        return this.triggerOption = orig.withRequiredArg().ofType(File.class);
    }

    @Override
    public void init(final @NonNull OptionConsumer parser) {
        this.reverseO = parser.accepts("reverse", "Reverse provided mapping files before applying");
    }

    @Override
    public Transformer.Factory create(final @NonNull OptionSet options) throws TransformerProvisionException {
        final List<File> files = options.valuesOf(this.triggerOption);
        final boolean reverse = options.has(this.reverseO);
        IMappingFile combined = null;
        for (final File file : files) {
            try {
                IMappingFile singleMapping = IMappingFile.load(file);
                if (reverse) {
                    singleMapping = singleMapping.reverse();
                }
                if (combined == null) {
                    combined = singleMapping;
                } else {
                    throw new TransformerProvisionException("Multiple mappings files are not yet supported");
                    // combined = combined.plus(singleMapping);
                }
            } catch (final IOException ex) {
                throw new TransformerProvisionException("Failed to read mappings from " + file, ex);
            }
        }
        return Transformer.renamerFactory(combined);
    }
}
