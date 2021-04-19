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

import org.junit.jupiter.api.Test;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.util.GsonUtils;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class IvyModuleWriterTest {

    @Test
    void testWriteVersionWithoutJavaVersion() throws IOException, XMLStreamException, TransformerException {
        final VersionDescriptor.Full version = GsonUtils.parseFromJson(this.getClass().getResource("manifest-1.16.5.json"), VersionDescriptor.Full.class);

        final StringWriter writer = new StringWriter();

        try (final IvyModuleWriter ivy = new IvyModuleWriter(writer)) {
            ivy.write(version, MinecraftPlatform.JOINED);
        }

        System.out.println(writer);

        // https://stackoverflow.com/questions/4616383/xmlstreamwriter-indentation
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        final StringWriter formattedOut = new StringWriter(writer.getBuffer().length() + 50);
        transformer.transform(new StreamSource(new StringReader(writer.toString())), new StreamResult(formattedOut));

        System.out.println(formattedOut);
    }

    @Test
    void testWriteVersionWithJavaVersion() throws IOException, XMLStreamException, TransformerException {
        final VersionDescriptor.Full version = GsonUtils.parseFromJson(this.getClass().getResource("manifest-21w15a.json"), VersionDescriptor.Full.class);

        final StringWriter writer = new StringWriter();

        try (final IvyModuleWriter ivy = new IvyModuleWriter(writer)) {
            ivy.write(version, MinecraftPlatform.JOINED);
        }

        System.out.println(writer);

        // https://stackoverflow.com/questions/4616383/xmlstreamwriter-indentation
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        final StringWriter formattedOut = new StringWriter(writer.getBuffer().length() + 50);
        transformer.transform(new StreamSource(new StringReader(writer.toString())), new StreamResult(formattedOut));

        System.out.println(formattedOut);
    }

}
