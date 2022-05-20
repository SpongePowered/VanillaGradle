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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class ShadowConfigurationApplier {

    private ShadowConfigurationApplier() {
    }

    public static void actuallyApplyShadowConfiguration(final TaskContainer tasks, final Configuration versionSource) {
        // Exclude anything in MC or its dependencies from being shadowed

        // because the configureEach action is only executed later but we close
        // the ClassLoader for this class immediately, we have to pre-load any
        // classes referenced within the filter.
        // whoo!
        MinecraftExclusionFilter.class.getName(); // load class
        tasks.withType(ShadowJar.class).configureEach(new Action<ShadowJar>() {
            @Override
            public void execute(final ShadowJar task) {
                task.dependencies(filter -> filter.exclude(new MinecraftExclusionFilter(versionSource)));
            }
        });
    }

    static class MinecraftExclusionFilter implements Spec<ResolvedDependency> {
        private final Set<String> minecraftNames = new HashSet<>();
        private Configuration versionSource;

        public MinecraftExclusionFilter(Configuration versionSource) {
            this.versionSource = versionSource;
        }

        @Override
        public boolean isSatisfiedBy(final ResolvedDependency dep) {
            if (versionSource != null) {
                resolveVersions();
                // Don't repeat this later.
                versionSource = null;
            }
            return this.minecraftNames.contains(dep.getName());
        }

        private void resolveVersions() {
            final ResolvedConfiguration conf = versionSource.getResolvedConfiguration();
            conf.rethrowFailure();
            final Queue<ResolvedDependency> deps = new ArrayDeque<>(conf.getFirstLevelModuleDependencies());
            ResolvedDependency pointer;
            while ((pointer = deps.poll()) != null) {
                if (this.minecraftNames.add(pointer.getName())) {
                    deps.addAll(pointer.getChildren());
                }
            }
        }
    }

}
