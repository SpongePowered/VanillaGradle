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
package org.spongepowered.gradle.vanilla.repository;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.spongepowered.gradle.vanilla.internal.model.GroupArtifactVersion;
import org.spongepowered.gradle.vanilla.internal.model.Library;
import org.spongepowered.gradle.vanilla.internal.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.internal.model.rule.OperatingSystemRule;
import org.spongepowered.gradle.vanilla.internal.model.rule.RuleContext;
import org.spongepowered.gradle.vanilla.internal.repository.IvyModuleWriter;
import org.spongepowered.gradle.vanilla.internal.util.GsonUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

public class IvyModuleWriterTest {

    private static final Pattern NEWLINE = Pattern.compile("\r?\n");

    private static final RuleContext TEST_CONTEXT = RuleContext.create();

    static {
        // Override OS information so we generate consistent ivy modules
        OperatingSystemRule.setOsName(IvyModuleWriterTest.TEST_CONTEXT, "Windows 10");
        OperatingSystemRule.setOsArchitecture(IvyModuleWriterTest.TEST_CONTEXT, "amd64");
        OperatingSystemRule.setOsVersion(IvyModuleWriterTest.TEST_CONTEXT, "10.0");
    }

    @Test
    void testWriteVersionWithoutJavaVersion() throws IOException, XMLStreamException {
        final VersionDescriptor.Full version = GsonUtils.parseFromJson(this.getClass().getResource("manifest-1.16.5.json"), VersionDescriptor.Full.class);

        final StringWriter writer = new StringWriter();

        try (final IvyModuleWriter ivy = new IvyModuleWriter(writer)) {
            ivy
                .dependencies(IvyModuleWriterTest.manifestLibraries(version, IvyModuleWriterTest.TEST_CONTEXT, lib -> !lib.isNatives()))
                .write(version, MinecraftPlatform.JOINED);
        }

        Assertions.assertLinesMatch(
            IvyModuleWriterTest.readLinesFromResource("expected-ivy-1.16.5.xml"),
            IvyModuleWriterTest.NEWLINE.splitAsStream(writer.getBuffer()).collect(Collectors.toList())
        );
    }

    @Test
    void testWriteVersionWithJavaVersion() throws IOException, XMLStreamException {
        final VersionDescriptor.Full version = GsonUtils.parseFromJson(this.getClass().getResource("manifest-21w15a.json"), VersionDescriptor.Full.class);

        final StringWriter writer = new StringWriter();

        try (final IvyModuleWriter ivy = new IvyModuleWriter(writer)) {
            ivy
                .dependencies(IvyModuleWriterTest.manifestLibraries(version, IvyModuleWriterTest.TEST_CONTEXT, lib -> !lib.isNatives()))
                .write(version, MinecraftPlatform.JOINED);
        }
    }

    private static Set<GroupArtifactVersion> manifestLibraries(
        final VersionDescriptor.Full manifest,
        final RuleContext rules,
        final Predicate<Library> filter
    ) {
        final Set<GroupArtifactVersion> ret = new LinkedHashSet<>();
        for (final Library library : manifest.libraries()) {
            if (library.rules().test(rules) && filter.test(library)) {
                ret.add(library.name());
            }
        }
        return Collections.unmodifiableSet(ret);
    }

    private static List<String> readLinesFromResource(final String resource) throws IOException {
        final @Nullable InputStream in = IvyModuleWriterTest.class.getResourceAsStream(resource);
        Assertions.assertNotNull(in, "No resource with name " + resource + " was found");

        final List<String> contents = new ArrayList<>();
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contents.add(line);
            }
        }
        return contents;
    }

}
