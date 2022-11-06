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
package org.spongepowered.gradle.vanilla.renamer.transformer;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import net.minecraftforge.fart.api.Transformer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.common.value.qual.MatchesRegex;
import org.immutables.metainf.Metainf;
import org.spongepowered.gradle.vanilla.renamer.spi.TransformerProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Metainf.Service
public final class FilterClassesTransformer implements TransformerProvider {

    private static final String ID = "only";

    private @MonotonicNonNull OptionSpec<String> triggerOption;

    @Override
    public @NonNull @MatchesRegex(TransformerProvider.ID_PATTERN_REGEX) String id() {
        return FilterClassesTransformer.ID;
    }

    @Override
    public OptionSpec<?> decorateTriggerOption(final @NonNull OptionSpecBuilder orig) {
        return this.triggerOption = orig.withRequiredArg();
    }

    @Override
    public Transformer.Factory create(final @NonNull OptionSet options) {
        final List<String> allowedPrefixes = options.valuesOf(this.triggerOption);
        return ctx -> new Action(new HashSet<>(allowedPrefixes));
    }

    static final class Action implements Transformer {

        private final String[] allowedPrefixes;

        Action(final Set<String> allowedPrefixes) {
            this.allowedPrefixes = allowedPrefixes.toArray(new String[0]);
        }

        @Override
        public ClassEntry process(final ClassEntry entry) {
            if (this.matches(entry.getName())) {
                return entry;
            } else {
                return null;
            }
        }

        private boolean matches(final String className) {
            if (!className.contains("/")) {
                return true;
            }

            for (final String pkg : this.allowedPrefixes) {
                if (className.startsWith(pkg)) {
                    return true;
                }
            }
            return false;

        }

        @Override
        public ResourceEntry process(final ResourceEntry entry) {
            return entry;
            /*if (!this.matches(entry.getConfig().getService().replace('.', '/'))) {
                return null;
            }

            final List<String> providers = new ArrayList<>(entry.getConfig().getProviders().size());
            for (final String provider : entry.getConfig().getProviders()) {
                if (this.matches(provider.replace('.', '/'))) {
                    providers.add(provider);
                }
            }
            if (providers.isEmpty()) {
                return null;
            }

            return new JarServiceProviderConfigurationEntry(entry.getTime(), new ServiceProviderConfiguration(entry.getConfig().getService(), providers));*/
        }
    }
}
