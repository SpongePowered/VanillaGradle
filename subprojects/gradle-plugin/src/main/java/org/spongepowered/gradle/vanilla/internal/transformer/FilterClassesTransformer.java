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
package org.spongepowered.gradle.vanilla.internal.transformer;

import net.minecraftforge.fart.api.Transformer;

import java.util.Set;

final class FilterClassesTransformer implements Transformer {

    private final String[] allowedPrefixes;

    FilterClassesTransformer(final Set<String> allowedPrefixes) {
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
