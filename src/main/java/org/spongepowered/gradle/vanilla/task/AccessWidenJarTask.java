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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;
import org.spongepowered.gradle.vanilla.util.DigestUtils;
import org.spongepowered.gradle.vanilla.worker.AccessWidenerWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

public abstract class AccessWidenJarTask extends DefaultTask {

    private final Property<String> accessWidenerHash;

    /**
     * Get the classpath used to execute the access widener worker.
     *
     * <p>This must contain the {@code net.fabricmc:access-widener} library and
     * its dependencies.</p>
     *
     * @return the classpath.
     */
    @Classpath
    public abstract FileCollection getWorkerClasspath();

    public abstract void setWorkerClasspath(final FileCollection classpath);

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

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
        this.getWorkerExecutor()
                .classLoaderIsolation(spec -> spec.getClasspath().from(this.getWorkerClasspath()))
                .submit(AccessWidenerWorker.class, params -> {
                    params.getAccessWideners().from(this.getAccessWideners());
                    params.getSource().from(this.getSource());
                    params.getDestination().set(this.getDestination());
                    params.getExpectedNamespace().set(this.getExpectedNamespace());
                });
    }

}
