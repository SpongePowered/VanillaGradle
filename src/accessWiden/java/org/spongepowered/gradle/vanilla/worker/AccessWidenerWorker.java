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
package org.spongepowered.gradle.vanilla.worker;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public abstract class AccessWidenerWorker implements WorkAction<AccessWidenerWorker.Parameters> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessWidenerWorker.class);

    /**
     * Parameters, described in the main task.
     */
    public static abstract class Parameters implements WorkParameters {
        public abstract ConfigurableFileCollection getSource();
        public abstract Property<String> getExpectedNamespace();
        public abstract ConfigurableFileCollection getAccessWideners();
        public abstract RegularFileProperty getDestination();
    }

    @Override
    public void execute() {
        final Parameters params = this.getParameters();
        final AccessWidener widener = new AccessWidener();
        final AccessWidenerReader reader = new AccessWidenerReader(widener);

        for (final File widenerFile : params.getAccessWideners().getAsFileTree()) {
            try (final BufferedReader fileReader =
                         new BufferedReader(new InputStreamReader(new FileInputStream(widenerFile), StandardCharsets.UTF_8))) {
                reader.read(fileReader);
            } catch (final IOException ex) {
                AccessWidenerWorker.LOGGER.error("Failed to read access widener from file {}", widenerFile, ex);
                throw new GradleException("Access widening failed");
            }
        };

        JarEntry entry = null;
        try (final InputStream source = new FileInputStream(params.getSource().getSingleFile());
             final JarInputStream sourceJar = new JarInputStream(source);
             final OutputStream destination = new FileOutputStream(params.getDestination().get().getAsFile());
             final JarOutputStream destinationJar = sourceJar.getManifest() == null ? new JarOutputStream(destination) :
                                                    new JarOutputStream(destination, sourceJar.getManifest())
        ) {

            while ((entry = sourceJar.getNextJarEntry()) != null) {
                destinationJar.putNextEntry(entry);
                if (entry.getName().endsWith(".class")) { // Possible class
                    this.transformEntry(widener, sourceJar, destinationJar);
                } else {
                    this.copyEntry(sourceJar, destinationJar);
                }
            }

        } catch (final IOException ex) {
            AccessWidenerWorker.LOGGER.error("Failed to access-transform jar {} (while processing entry {})",
                    params.getSource().getSingleFile(),
                    entry == null ? "<unknown>" : entry.getName(), ex);
            throw new GradleException("Unable to access-transform " + params.getSource().getSingleFile(), ex);
        }
    }

    private void transformEntry(final AccessWidener widener, final InputStream source, final OutputStream dest)
            throws IOException {
        final ClassReader reader = new ClassReader(source);
        final ClassWriter writer = new ClassWriter(reader, 0);
        // TODO: Expose the ASM version constant somewhere visible to this worker
        final ClassVisitor visitor = AccessWidenerVisitor.createClassVisitor(Opcodes.ASM9, writer, widener);
        reader.accept(visitor, 0);
        dest.write(writer.toByteArray());
    }

    private void copyEntry(final InputStream source, final OutputStream dest) throws IOException {
        final byte[] buf = new byte[4096];
        int read;
        while ((read = source.read(buf)) != -1) {
            dest.write(buf, 0, read);
        }
    }

}
