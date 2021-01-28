package org.spongepowered.gradle.vanilla.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.worker.JarDecompileWorker;

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

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void execute() {
        // Execute in an isolated class loader that can access our customized classpath
        this.getWorkerExecutor()
            .classLoaderIsolation(spec -> spec.getClasspath().from(this.getWorkerClasspath()))
            .submit(JarDecompileWorker.class, parameters -> {
                parameters.getInputJar().set(this.getInputJar());
                parameters.getOutputJar().set(this.getOutputJar());
            });
    }
}
