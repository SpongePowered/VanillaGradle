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
package org.spongepowered.gradle.vanilla.internal.runs;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.util.CheckedConsumer;
import org.spongepowered.gradle.vanilla.internal.util.IndentingXmlStreamWriter;
import org.spongepowered.gradle.vanilla.internal.util.StringUtils;
import org.spongepowered.gradle.vanilla.runs.RunConfiguration;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class EclipseRunConfigurationWriter implements AutoCloseable {

    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newInstance();

    private static final String RUNTIME_CLASSPATH_ENTRY = "runtimeClasspathEntry";

    private final boolean managedOutput;
    private final Writer output;
    private final XMLStreamWriter writer;
    private @MonotonicNonNull String projectName;

    public EclipseRunConfigurationWriter(final Path target) throws IOException, XMLStreamException {
        this.managedOutput = true;
        this.output = Files.newBufferedWriter(target);
        this.writer = new IndentingXmlStreamWriter(EclipseRunConfigurationWriter.OUTPUT_FACTORY.createXMLStreamWriter(this.output), Constants.INDENT);
    }

    public EclipseRunConfigurationWriter projectName(final String name) {
        this.projectName = name;
        return this;
    }

    public void write(final RunConfiguration run) throws XMLStreamException {
        if (this.projectName == null) {
            throw new IllegalStateException("Project name must be set");
        }

        this.writer.writeStartDocument("UTF-8", "1.0");
        this.writer.writeStartElement("launchConfiguration");
        this.writer.writeAttribute("type", this.launching("localJavaApplication"));

        // TODO: debug("core.MAPPED_RESOURCE_PATHS") maybe

        this.booleanAttribute(this.launching("ATTR_ATTR_USE_ARGFILE"), false);
        this.booleanAttribute(this.launching("ATTR_SHOW_CODEDETAILS_IN_EXCEPTION_MESSAGES"), true);
        this.booleanAttribute(this.launching("ATTR_USE_CLASSPATH_ONLY_JAR"), false);
        this.booleanAttribute(this.launching("ATTR_USE_START_ON_FIRST_THREAD"), true); // todo: only needed on macOS?
        this.listAttribute(this.launching("CLASSPATH"), Arrays.asList(
            // <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            // <runtimeClasspathEntry containerPath="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher
            // .StandardVMType/JavaSE-1.8/" javaProject="SpongeVanilla" path="1" type="4"/>
            this.runtimeClasspathEntry(xml -> {
                xml.writeEmptyElement(EclipseRunConfigurationWriter.RUNTIME_CLASSPATH_ENTRY);
                xml.writeAttribute(
                    "containerPath",
                    this.launching("JRE_CONTAINER") + '/' + this.debug("ui.launcher.StandardVMType/JavaSE-") + EclipseRunConfigurationWriter.javaVersionName(run.getTargetVersion().get()) + '/'
                );
                xml.writeAttribute("javaProject", this.projectName);
                xml.writeAttribute("path", "1");
                xml.writeAttribute("type", "4");
            }),
            this.runtimeClasspathEntry(xml -> {
                xml.writeStartElement(EclipseRunConfigurationWriter.RUNTIME_CLASSPATH_ENTRY);
                xml.writeAttribute("id", this.launching("classpathentry.defaultClasspath"));
                xml.writeEmptyElement("memento");
                xml.writeAttribute("exportedEntriesOnly", "false");
                xml.writeAttribute("project", this.projectName);
                xml.writeEndElement();
            })
            // todo: is there a better way to map this to a gradle classpath?
        ));
        this.booleanAttribute(this.launching("DEFAULT_CLASSPATH"), false);
        this.stringAttribute(
            this.launching("JRE_CONTAINER"),
            this.launching("JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-" + EclipseRunConfigurationWriter.javaVersionName(run.getTargetVersion().get()))
        );
        this.stringAttribute(this.launching("MAIN_TYPE"), run.getMainClass().get());
        this.stringAttribute(this.launching("MODULE_NAME"), this.projectName); // todo: can this be a JPMS module?
        this.stringAttribute(this.launching("PROGRAM_ARGUMENTS"), StringUtils.join(run.getAllArgumentProviders(), true));
        this.stringAttribute(this.launching("PROJECT_ATTR"), this.projectName);
        this.stringAttribute(this.launching("VM_ARGUMENTS"), StringUtils.join(run.getAllJvmArgumentProviders(), true));
        this.stringAttribute(this.launching("WORKING_DIRECTORY"), run.getWorkingDirectory().get().getAsFile().getAbsolutePath());

        this.mapAttribute(this.debug("core.environmentVariables"), run.getActualEnvironment());

        this.writer.writeEndElement();
        this.writer.writeEndDocument();
    }

    private String launching(final String value) {
        return "org.eclipse.jdt.launching." + value;
    }

    private String debug(final String value) {
        return "org.eclipse.debug." + value;
    }

    private void mapAttribute(final String key, final Map<String, String> map) throws XMLStreamException {
        this.writer.writeStartElement("mapAttribute");
        this.writer.writeAttribute("key", key);
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            this.writer.writeEmptyElement("mapEntry");
            this.writer.writeAttribute("key", entry.getKey());
            this.writer.writeAttribute("value", entry.getValue());
        }
        this.writer.writeEndElement();
    }

    private void listAttribute(final String key, final List<String> values) throws XMLStreamException {
        this.writer.writeStartElement("listAttribute");
        this.writer.writeAttribute("key", key);
        for (final String value : values) {
            this.writer.writeEmptyElement("listEntry");
            this.writer.writeAttribute("value", value);
        }
        this.writer.writeEndElement();
    }

    private void booleanAttribute(final String key, final boolean value) throws XMLStreamException {
        this.writer.writeEmptyElement("booleanAttribute");
        this.writer.writeAttribute("key", key);
        this.writer.writeAttribute("value", String.valueOf(value));
    }

    private void stringAttribute(final String key, final String value) throws XMLStreamException {
        this.writer.writeEmptyElement("stringAttribute");
        this.writer.writeAttribute("key", key);
        this.writer.writeAttribute("value", value);
    }

    private String runtimeClasspathEntry(final CheckedConsumer<XMLStreamWriter, XMLStreamException> action) throws XMLStreamException {
        final StringWriter writer = new StringWriter();

        final XMLStreamWriter xml = EclipseRunConfigurationWriter.OUTPUT_FACTORY.createXMLStreamWriter(writer);

        xml.writeStartDocument();
        action.accept(xml);
        xml.writeEndDocument();

        xml.close();

        return writer.toString();
    }

    private static String javaVersionName(final JavaLanguageVersion version) {
        final int value = version.asInt();
        if (value <= 8) {
            return "1." + value;
        } else {
            return String.valueOf(value);
        }
    }

    @Override
    public void close() throws IOException, XMLStreamException {
        this.writer.close();

        if (this.managedOutput) {
            this.output.close();
        }
    }
}
