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
package org.spongepowered.gradle.vanilla.internal.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.spongepowered.gradle.vanilla.internal.model.Argument;
import org.spongepowered.gradle.vanilla.internal.model.GroupArtifactVersion;
import org.spongepowered.gradle.vanilla.internal.model.rule.FeatureRule;
import org.spongepowered.gradle.vanilla.internal.model.rule.OperatingSystemRule;
import org.spongepowered.gradle.vanilla.internal.model.rule.RuleDeclarationTypeAdapter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

public final class GsonUtils {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ZonedDateTime.class, GsonSerializers.ZDT)
            .registerTypeAdapter(GroupArtifactVersion.class, GsonSerializers.GAV)
            .registerTypeAdapter(Pattern.class, GsonSerializers.PATTERN)
            .registerTypeAdapterFactory(new RuleDeclarationTypeAdapter.Factory(FeatureRule.INSTANCE, OperatingSystemRule.INSTANCE))
            .registerTypeAdapterFactory(new Argument.ArgumentTypeAdapter.Factory())
            .create();

    public static <T> T parseFromJson(final URL url, final Class<T> type) throws IOException {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return GsonUtils.GSON.fromJson(reader, type);
        }
    }

    public static <T> T parseFromJson(final Path path, final Class<T> type) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GsonUtils.GSON.fromJson(reader, type);
        }
    }

    public static <T> T parseFromJson(final File file, final Class<T> type) throws IOException {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            return GsonUtils.GSON.fromJson(reader, type);
        }
    }

    public static <T> void writeToJson(final Path path, final T value, final Class<T> type) throws IOException {
        try (final BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GsonUtils.GSON.toJson(value, type, writer);
        }
    }

    private GsonUtils() {
    }
}
