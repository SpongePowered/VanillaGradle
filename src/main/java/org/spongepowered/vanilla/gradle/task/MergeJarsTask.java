package org.spongepowered.vanilla.gradle.task;

import net.minecraftforge.mergetool.AnnotationVersion;
import net.minecraftforge.mergetool.Merger;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.spongepowered.vanilla.gradle.Constants;

import java.io.IOException;

import javax.inject.Inject;

public abstract class MergeJarsTask extends DefaultTask {

    public MergeJarsTask() {
        this.setGroup(Constants.TASK_GROUP);
    }

    @InputFile
    public abstract RegularFileProperty getClientJar();

    @InputFile
    public abstract RegularFileProperty getServerJar();

    @OutputFile
    public abstract RegularFileProperty getMergedJar();

    @TaskAction
    public void execute() throws IOException {
        final Merger merger = new Merger(this.getClientJar().get().getAsFile(), this.getServerJar().get().getAsFile(), this.getMergedJar().get().getAsFile());
        merger.annotate(AnnotationVersion.API, true);
        merger.keepData();
        merger.process();
    }
}
