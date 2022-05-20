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
package org.spongepowered.gradle.vanilla.internal;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Resolves the names of the Minecraft libraries, for use with the Shadow plugin.
 */
public class ResolveMinecraftLibNames implements Callable<Set<String>> {
    private final NamedDomainObjectProvider<Configuration> minecraftConfig;
    private Set<String> result;

    public ResolveMinecraftLibNames(NamedDomainObjectProvider<Configuration> minecraftConfig) {
        this.minecraftConfig = minecraftConfig;
    }

    @Override
    public Set<String> call() {
        if (this.result == null) {
            final ResolvedConfiguration conf = minecraftConfig.get().getResolvedConfiguration();
            conf.rethrowFailure();
            this.result = new HashSet<>();
            final Queue<ResolvedDependency> deps = new ArrayDeque<>(conf.getFirstLevelModuleDependencies());
            ResolvedDependency pointer;
            while ((pointer = deps.poll()) != null) {
                if (this.result.add(pointer.getName())) {
                    deps.addAll(pointer.getChildren());
                }
            }
        }
        return result;
    }
}
