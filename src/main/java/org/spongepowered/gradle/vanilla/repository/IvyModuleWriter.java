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
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.model.GroupArtifactVersion;
import org.spongepowered.gradle.vanilla.model.JavaRuntimeVersion;
import org.spongepowered.gradle.vanilla.model.Library;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.model.rule.RuleContext;
import org.spongepowered.gradle.vanilla.util.IndentingXmlStreamWriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class IvyModuleWriter implements AutoCloseable {

    /**
     * An extra property, optional, that indicates the Java version a certain
     * Minecraft version was built against.
     */
    public static final String PROPERTY_JAVA_VERSION = "javaVersion";

    /**
     * The actual status provided for the artifact in Mojang metadata.
     */
    public static final String PROPERTY_MOJANG_STATUS = "mojangStatus";

    private static final String XSI = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    private static final String IVY = "http://ant.apache.org/ivy/schemas/ivy.xsd";
    private static final String VANILLAGRADLE = "https://spongepowered.org/vanillagradle/ivy-extra";

    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newInstance();

    private final boolean managedOutput;
    private final Writer output;
    private final XMLStreamWriter writer;

    public IvyModuleWriter(final Writer output) throws XMLStreamException {
        this.managedOutput = false;
        this.output = output;
        this.writer = new IndentingXmlStreamWriter(IvyModuleWriter.OUTPUT_FACTORY.createXMLStreamWriter(output), Constants.INDENT);
    }

    public IvyModuleWriter(final Path target) throws IOException, XMLStreamException {
        this.managedOutput = true;
        this.output = Files.newBufferedWriter(target);
        this.writer = new IndentingXmlStreamWriter(IvyModuleWriter.OUTPUT_FACTORY.createXMLStreamWriter(this.output), Constants.INDENT);
    }

    public void write(final VersionDescriptor.Full descriptor, final MinecraftPlatform platform, final RuleContext rules) throws XMLStreamException {
        this.writer.writeStartDocument("UTF-8", "1.0");
        this.writer.writeStartElement("ivy-module");
        this.writer.writeNamespace("xsi", IvyModuleWriter.XSI);
        this.writer.writeNamespace("vanillagradle", IvyModuleWriter.VANILLAGRADLE);
        this.writer.writeAttribute(IvyModuleWriter.XSI, "noNamespaceSchemaLocation", IvyModuleWriter.IVY);
        this.writer.writeAttribute("version", "2.0");

        this.writeInfo(descriptor, platform);
        this.writeDependencies(descriptor.libraries(), platform, rules);
        this.writeArtifacts(platform);

        this.writer.writeEndElement();
        this.writer.writeEndDocument();
    }

    private void writeInfo(final VersionDescriptor.Full version, final MinecraftPlatform platform) throws XMLStreamException {
        this.writer.writeStartElement("info");
        // Common attributes
        this.writer.writeAttribute("organisation", MinecraftPlatform.GROUP);
        this.writer.writeAttribute("module", platform.artifactId());
        this.writer.writeAttribute("revision", version.id());
        this.writer.writeAttribute("status", "release"); // gradle wants release... we must please the gradle...

        // License
        this.writer.writeEmptyElement("license");
        this.writer.writeAttribute("name", "Minecraft EULA");
        this.writer.writeAttribute("url", "https://www.minecraft.net/en-us/eula");

        // Extra info (this is the only type of custom metadata exposed in Gradle, for some reason...)
        this.writer.writeStartElement(IvyModuleWriter.VANILLAGRADLE, IvyModuleWriter.PROPERTY_MOJANG_STATUS);
        this.writer.writeCharacters(version.type().id());
        this.writer.writeEndElement();

        final @Nullable JavaRuntimeVersion javaVersion = version.javaVersion();
        if (javaVersion != null) {
            this.writer.writeStartElement(IvyModuleWriter.VANILLAGRADLE, IvyModuleWriter.PROPERTY_JAVA_VERSION);
            this.writer.writeCharacters(String.valueOf(javaVersion.majorVersion()));
            this.writer.writeEndElement();
        }

        // End
        this.writer.writeEndElement();
    }

    private void writeDependencies(
        final List<Library> libraries,
        final MinecraftPlatform platform,
        final RuleContext rules
    ) throws XMLStreamException {
        this.writer.writeStartElement("dependencies");

        final Set<GroupArtifactVersion> seenDependencies = new HashSet<>();
        for (final MinecraftSide side : platform.activeSides()) {
            side.applyLibraries(
                desc -> {
                    if (seenDependencies.add(desc)) {
                        this.writer.writeEmptyElement("dependency");
                        this.writer.writeAttribute("org", desc.group());
                        this.writer.writeAttribute("name", desc.artifact());
                        this.writer.writeAttribute("rev", desc.version());
                        this.writer.writeAttribute("transitive", "false");
                    }
                },
                libraries,
                rules
            );
        }

        this.writer.writeEndElement();
    }

    private void writeArtifacts(final MinecraftPlatform platform) throws XMLStreamException {
        // TODO: Maybe needed for sources jar?
        /*this.writer.writeStartElement("publications");
        this.writer.writeEmptyElement("artifact");
        this.writer.writeAttribute("name", platform.artifactId());
        this.writer.writeAttribute("type", "jar");
        this.writer.writeAttribute("ext", "jar");
        this.writer.writeAttribute("conf", "default");
        this.writer.writeEndElement(); */
    }

    @Override
    public void close() throws IOException, XMLStreamException {
        this.writer.close();

        if (this.managedOutput) {
            this.output.close();
        }
    }
}
