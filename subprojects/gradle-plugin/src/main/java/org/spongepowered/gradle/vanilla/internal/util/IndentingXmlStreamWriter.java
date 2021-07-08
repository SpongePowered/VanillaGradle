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

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class IndentingXmlStreamWriter implements XMLStreamWriter {

    private final XMLStreamWriter backing;
    private final String indent;
    private int indentLevel;
    private boolean inTag;

    public IndentingXmlStreamWriter(final XMLStreamWriter backing, final String indent) {
        this.backing = backing;
        this.indent = indent;
    }

    private void newline() throws XMLStreamException {
        this.backing.writeCharacters("\n");
    }

    private void unwindIndent() throws XMLStreamException {
        if (this.inTag) {
            this.inTag = false;
            this.newline();

            for (int i = 0; i < this.indentLevel; ++i) {
                this.backing.writeCharacters(this.indent);
            }
        }
    }

    /* (non-Javadoc)
     * Indentation is controlled by tags -- every tag should get its own line.
     * We have less flexibility in a streaming context, but basically:
     * start tag -> increase indent level
     * end tag -> decrease indent level
     * any operation that can complete a tag (another start, end, text contests) -> print newline followed by indent
     */

    // Element boundaries

    private void handleStart() throws XMLStreamException {
        this.unwindIndent();
        this.inTag = true;
        this.indentLevel++;
    }

    @Override
    public void writeStartElement(final String localName) throws XMLStreamException {
        this.handleStart();
        this.backing.writeStartElement(localName);
    }

    @Override
    public void writeStartElement(final String namespaceURI, final String localName) throws XMLStreamException {
        this.handleStart();
        this.backing.writeStartElement(namespaceURI, localName);
    }

    @Override
    public void writeStartElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        this.handleStart();
        this.backing.writeStartElement(prefix, localName, namespaceURI);
    }

    @Override
    public void writeEmptyElement(final String namespaceURI, final String localName) throws XMLStreamException {
        this.unwindIndent();
        this.inTag = true;
        this.backing.writeEmptyElement(namespaceURI, localName);
    }

    @Override
    public void writeEmptyElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        this.unwindIndent();
        this.inTag = true;
        this.backing.writeEmptyElement(prefix, localName, namespaceURI);
    }

    @Override
    public void writeEmptyElement(final String localName) throws XMLStreamException {
        this.unwindIndent();
        this.inTag = true;
        this.backing.writeEmptyElement(localName);
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        // Assume we always have an indent to unwind here
        this.inTag = true;
        this.indentLevel--;
        this.unwindIndent();
        // Then flag for unwinding again when the next element is written
        this.inTag = true;
        this.backing.writeEndElement();
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        this.newline();
        this.backing.writeEndDocument();
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
        this.backing.writeStartDocument();
        this.inTag = true;
    }

    @Override
    public void writeStartDocument(final String version) throws XMLStreamException {
        this.backing.writeStartDocument(version);
        this.inTag = true;
    }

    @Override
    public void writeStartDocument(final String encoding, final String version) throws XMLStreamException {
        this.backing.writeStartDocument(encoding, version);
        this.inTag = true;
    }

    // Operations that can exit a tag definition

    @Override
    public void writeComment(final String data) throws XMLStreamException {
        this.unwindIndent();
        this.backing.writeComment(data);
    }

    @Override
    public void writeProcessingInstruction(final String target) throws XMLStreamException {
        this.unwindIndent();
        this.backing.writeProcessingInstruction(target);
    }

    @Override
    public void writeProcessingInstruction(final String target, final String data) throws XMLStreamException {
        this.unwindIndent();
        this.backing.writeProcessingInstruction(target, data);
    }

    @Override
    public void writeCData(final String data) throws XMLStreamException {
        this.unwindIndent();
        this.backing.writeCData(data);
    }

    @Override
    public void writeDTD(final String dtd) throws XMLStreamException {
        this.unwindIndent();
        this.backing.writeDTD(dtd);
    }

    @Override
    public void writeEntityRef(final String name) throws XMLStreamException {
        this.unwindIndent();
        this.backing.writeEntityRef(name);
    }

    @Override
    public void writeCharacters(final String text) throws XMLStreamException {
        this.unwindIndent();
        this.backing.writeCharacters(text);
    }

    @Override
    public void writeCharacters(final char[] text, final int start, final int len) throws XMLStreamException {
        this.unwindIndent();
        this.backing.writeCharacters(text, start, len);
    }

    // Directly passed through to the backing stream since they won't change parser state
    // @formatter:off

    @Override public void writeAttribute(final String localName, final String value) throws XMLStreamException { this.backing.writeAttribute(localName, value); }
    @Override public void writeAttribute(final String prefix, final String namespaceURI, final String localName, final String value) throws XMLStreamException { this.backing.writeAttribute(prefix, namespaceURI, localName, value); }
    @Override public void writeAttribute(final String namespaceURI, final String localName, final String value) throws XMLStreamException { this.backing.writeAttribute(namespaceURI, localName, value); }
    @Override public void writeNamespace(final String prefix, final String namespaceURI) throws XMLStreamException { this.backing.writeNamespace(prefix, namespaceURI); }
    @Override public void writeDefaultNamespace(final String namespaceURI) throws XMLStreamException { this.backing.writeDefaultNamespace(namespaceURI); }
    @Override public String getPrefix(final String uri) throws XMLStreamException { return this.backing.getPrefix(uri); }
    @Override public void setPrefix(final String prefix, final String uri) throws XMLStreamException { this.backing.setPrefix(prefix, uri); }
    @Override public void setDefaultNamespace(final String uri) throws XMLStreamException { this.backing.setDefaultNamespace(uri); }
    @Override public void setNamespaceContext(final NamespaceContext context) throws XMLStreamException { this.backing.setNamespaceContext(context); }
    @Override public NamespaceContext getNamespaceContext() { return this.backing.getNamespaceContext(); }
    @Override public Object getProperty(final String name) throws IllegalArgumentException { return this.backing.getProperty(name); }
    @Override public void close() throws XMLStreamException { this.backing.close(); }
    @Override public void flush() throws XMLStreamException { this.backing.flush(); }

    // @formatter:on

}
