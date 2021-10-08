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
package org.spongepowered.gradle.vanilla.internal.repository;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.gradle.vanilla.internal.Constants;

import java.util.Arrays;

/**
 * Tools used for specific operations in the Minecraft preparation pipeline.
 */
public enum ResolvableTool {
    JAR_MERGE(Constants.Configurations.MERGETOOL, Constants.WorkerDependencies.MERGE_TOOL),
    ACCESS_WIDENER(Constants.Configurations.ACCESS_WIDENER, Constants.WorkerDependencies.ACCESS_WIDENER),
    REMAP_TINY(Constants.Configurations.REMAP_TINY, new Dependency(Constants.WorkerDependencies.LORENZ_TINY).setNonTransitive(), new Dependency(Constants.WorkerDependencies.MAPPING_IO)),
    REMAP_PARCHMENT(Constants.Configurations.REMAP_PARCHMENT, Constants.WorkerDependencies.FEATHER, Constants.WorkerDependencies.FEATHER_IO_GSON),
    ;

    private final String id;
    private final Dependency[] dependencies;

    ResolvableTool(final String id, final String... notations) {
        this(id, Arrays.stream(notations).map(Dependency::new).toArray(Dependency[]::new));
    }

    ResolvableTool(final String id, final Dependency... dependencies) {
        this.id = id;
        this.dependencies = dependencies;
    }

    public String id() {
        return this.id;
    }

    public Dependency[] dependencies() {
        return this.dependencies;
    }

    public enum Repository {
        ;

        private final String url;

        Repository(String url) {
            this.url = url;
        }

        public String url() {
            return url;
        }
    }

    public static class Dependency {
        private final String notation;
        private @Nullable Repository repo = null;
        private boolean isTransitive = true;

        public Dependency(final String notation) {
            this.notation = notation;
        }

        public Dependency setNonTransitive() {
            this.isTransitive = false;
            return this;
        }

        public Dependency setRepo(Repository repo) {
            this.repo = repo;
            return this;
        }

        public String notation() {
            return notation;
        }

        public Repository repo() {
            return repo;
        }

        public boolean isTransitive() {
            return isTransitive;
        }
    }

    public static class MissingToolException extends RuntimeException {
        public MissingToolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
