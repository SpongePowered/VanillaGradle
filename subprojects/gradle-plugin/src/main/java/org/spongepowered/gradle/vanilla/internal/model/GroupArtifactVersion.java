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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

@Value.Immutable(builder = false)
public abstract class GroupArtifactVersion {

    public static GroupArtifactVersion of(
            final String group,
            final String artifact,
            final @Nullable String version
    ) {
        return new GroupArtifactVersionImpl(group, artifact, version, null);
    }

    public static GroupArtifactVersion of(
            final String group,
            final String artifact,
            final @Nullable String version,
            final @Nullable String classifier
    ) {
        return new GroupArtifactVersionImpl(group, artifact, version, classifier);
    }

    public static GroupArtifactVersion parse(final String notation) {
        final String[] split = notation.split(":");
        if (split.length > 4 || split.length < 2) {
            throw new IllegalArgumentException("Unsupported notation '" + notation + "', must be in the format of group:artifact[:version[:classifier]]");
        }
        return GroupArtifactVersion.of(
                split[0],
                split[1],
                split.length > 2 ? split[2] : null,
                split.length > 3 ? split[3] : null
        );
    }

    GroupArtifactVersion() {
    }

    @Value.Parameter
    public abstract String group();

    @Value.Parameter
    public abstract String artifact();

    @Value.Parameter
    public abstract @Nullable String version();

    @Value.Parameter
    public abstract @Nullable String classifier();

    @Override
    public final String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append(this.group()).append(':').append(this.artifact());

        @Nullable final String version = this.version();
        if (version != null) {
            builder.append(':').append(version);

            @Nullable final String classifier = this.classifier();

            if (classifier != null) {
                builder.append(':').append(classifier);
            }
        }

        return builder.toString();
    }
}
