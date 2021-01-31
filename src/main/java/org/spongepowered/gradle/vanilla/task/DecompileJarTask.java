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

import com.sun.management.OperatingSystemMXBean;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.worker.JarDecompileWorker;


import java.lang.management.ManagementFactory;

import javax.inject.Inject;

public abstract class DecompileJarTask extends DefaultTask {

    public DecompileJarTask() {
        this.setGroup(Constants.TASK_GROUP);
    }

    /**
     * Get the classpath used to execute the jar decompile worker.
     *
     * <p>This must contain the {@code net.minecraftforge:forgeflower} library and
     * its dependencies.</p>
     *
     * @return the classpath.
     */
    @Classpath
    public abstract FileCollection getWorkerClasspath();

    public abstract void setWorkerClasspath(final FileCollection collection);

    @Classpath
    public abstract ConfigurableFileCollection getDecompileClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void execute() {
        // Execute in an isolated class loader that can access our customized classpath
        final long totalSystemMemoryBytes =
                ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize() / (1024L * 1024L);
        this.getWorkerExecutor().processIsolation(spec -> {
            spec.forkOptions(options -> {
                options.setMaxHeapSize(Math.max(totalSystemMemoryBytes / 4, 4096) + "M");
            });
            spec.getClasspath().from(this.getWorkerClasspath());
        }).submit(JarDecompileWorker.class, parameters -> {
            parameters.getDecompileClasspath().from(this.getDecompileClasspath());
            parameters.getInputJar().set(this.getInputJar());
            parameters.getOutputJar().set(this.getOutputJar());
        });
    }
}
