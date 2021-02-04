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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.worker.JarMergeWorker;

import javax.inject.Inject;

@CacheableTask
public abstract class MergeJarsTask extends DefaultTask implements ProcessedJarTask {

    /**
     * Get the classpath used to execute the jar merge worker.
     *
     * <p>This must contain the {@code net.minecraftforge:mergetool} library and
     * its dependencies.</p>
     *
     * @return the classpath.
     */
    @Classpath
    public abstract FileCollection getWorkerClasspath();

    public abstract void setWorkerClasspath(final FileCollection collection);

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getClientJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getServerJar();

    @OutputFile
    public abstract RegularFileProperty getMergedJar();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void execute() {
        // Execute in an isolated class loader that can access our customized classpath
        this.getWorkerExecutor()
                .classLoaderIsolation(spec -> spec.getClasspath().from(this.getWorkerClasspath()))
                .submit(JarMergeWorker.class, parameters -> {
                    parameters.getClientJar().set(this.getClientJar());
                    parameters.getServerJar().set(this.getServerJar());
                    parameters.getMergedJar().set(this.getMergedJar());
                });
    }

    @Override
    public RegularFileProperty outputJar() {
        return this.getMergedJar();
    }
}
