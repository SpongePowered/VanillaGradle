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
package org.spongepowered.gradle.vanilla.internal.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Transforms the content of a jar.
 */
public class JarTransformer {
    private final UnaryOperator<ClassVisitor>[] transformers;

    private JarTransformer(final UnaryOperator<ClassVisitor>[] transformers) {
        this.transformers = transformers;
    }

    /**
     * Transforms the content of the given jar.
     *
     * @param inputJar The input jar.
     * @param outputJar The output jar.
     * @throws IOException if an error occurs while reading or writing the jars.
     */
    public void transform(final Path inputJar, final Path outputJar) throws IOException {
        try (final ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(inputJar));
             final ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(outputJar))) {

            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                zipOut.putNextEntry(entry);

                final String name = entry.getName();
                if (name.endsWith(".class")) {
                    this.transformClass(zipIn, zipOut);
                } else if (name.equals("META-INF/MANIFEST.MF")) {
                    this.transformManifest(zipIn, zipOut);
                } else {
                    zipIn.transferTo(zipOut);
                }

                zipOut.closeEntry();
            }
        }
    }

    /**
     * Applies class transformers.
     *
     * @param in The input stream.
     * @param out The output stream.
     * @throws IOException if an error occurs while reading or writing the entry.
     */
    private void transformClass(final InputStream in, final OutputStream out) throws IOException {
        final ClassReader reader = new ClassReader(in.readAllBytes());
        final ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = writer;
        for (final UnaryOperator<ClassVisitor> transformer : this.transformers) {
            visitor = transformer.apply(visitor);
        }
        reader.accept(visitor, 0);
        out.write(writer.toByteArray());
    }

    /**
     * Strips all class signatures.
     *
     * @param in The input stream.
     * @param out The output stream.
     * @throws IOException if an error occurs while reading or writing the entry.
     */
    private void transformManifest(final InputStream in, final OutputStream out) throws IOException {
        final Manifest manifest = new Manifest(in);
        manifest.getEntries().entrySet().removeIf((entry) -> {
            final String name = entry.getKey();
            if (!name.endsWith(".class")) {
                return false;
            }

            final Attributes attributes = entry.getValue();
            attributes.entrySet().removeIf((attribute) -> {
                final String key = attribute.getKey().toString().toLowerCase(Locale.ROOT);
                return key.endsWith("-digest");
            });
            return attributes.isEmpty();
        });
        manifest.write(out);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<UnaryOperator<ClassVisitor>> transformers = new ArrayList<>();

        private Builder() {}

        public Builder add(final UnaryOperator<ClassVisitor> transformer) {
            this.transformers.add(Objects.requireNonNull(transformer, "transformer"));
            return this;
        }

        public Builder add(final ClassTransformerProvider provider) {
            return this.add(Objects.requireNonNull(provider, "provider").provide());
        }

        @SuppressWarnings("unchecked")
        public JarTransformer build() {
            return new JarTransformer(this.transformers.toArray(new UnaryOperator[0]));
        }
    }
}
