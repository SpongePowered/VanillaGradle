package org.spongepowered.vanilla.gradle.task;

import net.minecraftforge.mergetool.AnnotationVersion;
import net.minecraftforge.mergetool.Merger;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

import javax.inject.Inject;

public class MergeJarsTask extends DefaultTask {

    private final RegularFileProperty clientJar;
    private final RegularFileProperty serverJar;
    private final RegularFileProperty mergedJar;

    @Inject
    public MergeJarsTask(final ObjectFactory factory) {
        this.clientJar = factory.fileProperty();
        this.serverJar = factory.fileProperty();
        this.mergedJar = factory.fileProperty();
    }

    @InputFile
    public RegularFileProperty getClientJar() {
        return this.clientJar;
    }

    @InputFile
    public RegularFileProperty getServerJar() {
        return this.serverJar;
    }

    @OutputFile
    public RegularFileProperty getMergedJar() {
        return this.mergedJar;
    }

    @TaskAction
    public void execute() {
        final Merger merger = new Merger(this.clientJar.get().getAsFile(), this.serverJar.get().getAsFile(), this.mergedJar.get().getAsFile());
        merger.annotate(AnnotationVersion.API, true);
        try {
            merger.process();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
