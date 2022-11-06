package org.spongepowered.gradle.vanilla.remap.task;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Produce a remapped artifact
 */
public abstract class RemapJar extends Jar {

    @InputFiles
    public abstract ConfigurableFileCollection getToolClasspath();

    @Nested
    public abstract ListProperty<CommandLineArgumentProvider> getToolArguments();

    @Input
    public abstract Property<String> getToolMainClass();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Override
    protected void copy() {
        super.copy();

        // todo: use this somewhere??
        final File output = this.getArchiveFile().get().getAsFile();

        // Post-process??
        this.getWorkerExecutor().classLoaderIsolation(spec -> {
            spec.getClasspath().from(this.getToolClasspath());
        }).submit(RemapWorkAction.class, params -> {
            params.getMainClass().set(this.getToolMainClass());
            params.getArguments().set(this.getToolArguments().map(args -> {
                final List<String> ret = new ArrayList<>();
                for (final CommandLineArgumentProvider argumentProvider : args) {
                    for (final String arg : argumentProvider.asArguments()) {
                        ret.add(arg);
                    }
                }
                return ret;
            }));
        });
        this.getWorkerExecutor().await();
    }


    static abstract class RemapWorkAction implements WorkAction<RemapWorkAction.Params> {
        interface Params extends WorkParameters {
            ListProperty<String> getArguments();
            Property<String> getMainClass();
        }

        /**
         * The work to perform when this work item executes.
         */
        @Override
        public void execute() {
            try {
                Class.forName(this.getParameters().getMainClass().get())
                    .getMethod("main", String[].class)
                    .invoke(null, (Object[]) this.getParameters().getArguments().get().toArray(new String[0]));
            } catch (final IllegalAccessException | ClassNotFoundException | NoSuchMethodException ex) {
                throw new GradleException("Failed to invoke tool main class", ex);
            } catch (final InvocationTargetException ex) {
                if (ex.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) ex.getCause();
                } else {
                    throw new GradleException("Failed during tool action", ex.getCause());
                }
            }
        }
    }
}
