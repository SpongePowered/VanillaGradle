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
package org.spongepowered.gradle.vanilla.task;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.util.DigestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import javax.inject.Inject;

public abstract class AccessWidenJarTask extends DefaultTask {

    private final Property<String> accessWidenerHash;

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @InputFiles
    public abstract ConfigurableFileCollection getSource();

    /**
     * The namespace the access widener should be in to widen a jar
     *
     * @return the expected namespace value
     */
    @Input
    @Optional
    public abstract Property<String> getExpectedNamespace();

    @Internal
    public abstract Property<String> getArchiveClassifier();

    @InputFiles
    public abstract ConfigurableFileCollection getAccessWideners();

    @Internal // calculated from the input file
    public Provider<String> getAccessWidenerHash() {
        return this.accessWidenerHash;
    }


    @Internal
    public abstract DirectoryProperty getDestinationDirectory();

    @OutputFile
    public abstract RegularFileProperty getDestination();

    public AccessWidenJarTask() {
        this.onlyIf(t -> !this.getAccessWideners().isEmpty());
        this.accessWidenerHash = this.getObjectFactory().property(String.class)
                .value(this.getProviderFactory().provider(() -> {
                    try {
                        final MessageDigest digest = MessageDigest.getInstance("SHA-1");
                        for (final File widenerFile : this.getAccessWideners().getAsFileTree()) {
                            try (final InputStream is = new FileInputStream(widenerFile)) {
                                final byte[] buf = new byte[4096];
                                int read;
                                while ((read = is.read(buf)) != -1) {
                                    digest.update(buf, 0, read);
                                }
                            }
                        }
                        return DigestUtils.toHexString(digest.digest());
                    } catch (final IOException | NoSuchAlgorithmException ex) {
                        throw new RuntimeException(ex);
                    }
                }));
        this.accessWidenerHash.finalizeValueOnRead();

        this.getDestination().set(this.getDestinationDirectory().zip(this.getAccessWidenerHash(), (dir, hash) -> {
            final String classifier = this.getArchiveClassifier().get();
            return dir.file(hash + "-" + classifier + ".jar");
        }));
        this.getArchiveClassifier().convention("aw");
    }

    @TaskAction
    public void execute() {
        final AccessWidener widener = new AccessWidener();
        final AccessWidenerReader reader = new AccessWidenerReader(widener);

        for (final File widenerFile : this.getAccessWideners().getAsFileTree()) {
            try (final BufferedReader fileReader =
                         new BufferedReader(new InputStreamReader(new FileInputStream(widenerFile), StandardCharsets.UTF_8))) {
                reader.read(fileReader);
            } catch (final IOException ex) {
                this.getLogger().error("Failed to read access widener from file {}", widenerFile, ex);
                throw new GradleException("Access widening failed");
            }
        };

        JarEntry entry = null;
        try (final InputStream source = new FileInputStream(this.getSource().getSingleFile());
             final JarInputStream sourceJar = new JarInputStream(source);
             final OutputStream destination = new FileOutputStream(this.getDestination().get().getAsFile());
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
            this.getLogger().error("Failed to access-transform jar {} (while processing entry {})",
                    this.getSource().getSingleFile(),
                    entry == null ? "<unknown>" : entry.getName(), ex);
            throw new GradleException("Unable to access-transform " + this.getSource().getSingleFile(), ex);
        }
    }

    private void transformEntry(final AccessWidener widener, final InputStream source, final OutputStream dest)
            throws IOException {
        final ClassReader reader = new ClassReader(source);
        final ClassWriter writer = new ClassWriter(reader, 0);
        final ClassVisitor visitor = AccessWidenerVisitor.createClassVisitor(Constants.ASM_VERSION, writer, widener);
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
