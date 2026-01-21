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

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

public abstract class DumpClassTask extends DefaultTask {

    @InputFiles
    public abstract ConfigurableFileCollection getMinecraftClasspath();

    @Classpath
    public abstract ConfigurableFileCollection getAsmUtilClasspath();

    @Input
    @Option(option = "class", description = "The binary name of a class to dump")
    public abstract Property<String> getDumpedClass();

    @Input
    @Option(option = "asm", description = "Whether to print ASMifier (if given) or Textifier (if not) format")
    public abstract Property<Boolean> getUseAsmifier();

    @OutputFile
    @Optional
    @Option(option = "to", description = "A file to write the class dump to")
    public abstract Property<String> getDestinationFile();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    public DumpClassTask() {
        this.setDescription("Dump the bytecode of a class from Minecraft or any of its dependencies");
        this.getUseAsmifier().convention(false);
    }

    @TaskAction
    public void run() {
        this.getWorkerExecutor().classLoaderIsolation(spec -> spec.getClasspath().from(this.getAsmUtilClasspath()))
            .submit(DumpClassAction.class, params -> {
                params.getMinecraftClasspath().from(this.getMinecraftClasspath());
                params.getDumpedClass().set(this.getDumpedClass());
                params.getUseAsmifier().set(this.getUseAsmifier());
                params.getDestinationFile().set(this.getDestinationFile().flatMap(path -> this.getProjectLayout().getBuildDirectory().file(path)));
            });
        this.getWorkerExecutor().await();
    }


    // allow changing the ASM version from within the buildscript
    public abstract static class DumpClassAction implements WorkAction<DumpClassAction.Parameters> {

        private static final Logger LOGGER = Logging.getLogger(DumpClassAction.class);

        interface Parameters extends WorkParameters {
            ConfigurableFileCollection getMinecraftClasspath();
            Property<String> getDumpedClass();
            Property<Boolean> getUseAsmifier();
            RegularFileProperty getDestinationFile();
        }

        @Override
        public void execute() {
            final String fileName = this.getParameters().getDumpedClass().get().replace('.', '/') + ".class";
            final Printer classDumper = this.getParameters().getUseAsmifier().get() ? new ASMifier() : new Textifier();
            final StringWriter writer = new StringWriter();
            AutoCloseable closeable = null;
            try {
                // resolve class in classpath
                InputStream input = null;
                for (final File file : this.getParameters().getMinecraftClasspath()) {
                    if (file.isDirectory()) {
                        final File child = new File(file, fileName);
                        if (child.isFile()) {
                            input = new FileInputStream(child);
                            break;
                        }
                    } else if (file.getName().endsWith(".jar")) {
                        final ZipFile zf = new ZipFile(file);
                        final ZipEntry entry = zf.getEntry(fileName);
                        if (entry != null) {
                            closeable = zf;
                            input = zf.getInputStream(entry);
                            break;
                        }
                        zf.close();
                    }
                }

                if (input == null) {
                    throw new InvalidUserDataException("No class was found with the name '" + fileName + "'");
                }

                try (final PrintWriter pw = new PrintWriter(writer)) {
                    new ClassReader(input).accept(new TraceClassVisitor(null, classDumper, pw), 0);
                }
            } catch (final IOException ex) {
                DumpClassAction.LOGGER.error("Error while attempting to resolve class", ex);
            } finally {
                if (closeable != null) {
                    try {
                        closeable.close();
                    } catch (final Exception ex) {
                        DumpClassAction.LOGGER.error("Failed to close resource for target class file", ex);
                    }
                }
            }

            if (this.getParameters().getDestinationFile().isPresent()) {
                final Path dest = this.getParameters().getDestinationFile().get().getAsFile().toPath().toAbsolutePath();
                try (final BufferedWriter fileWriter = Files.newBufferedWriter(dest)) {
                    fileWriter.write(writer.toString());
                    DumpClassAction.LOGGER.lifecycle("Successfully dumped contents of class {} to {}", fileName, dest);
                } catch (final IOException ex) {
                    throw new GradleException("Failed to write class " + fileName + " to " + dest, ex);
                }
            } else {
                // then (finally) write it
                DumpClassAction.LOGGER.lifecycle("{}", writer);
            }
        }
    }

}
