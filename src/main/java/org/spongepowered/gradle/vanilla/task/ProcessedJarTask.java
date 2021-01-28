package org.spongepowered.gradle.vanilla.task;

import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;

public interface ProcessedJarTask extends Task {

    RegularFileProperty outputJar();
}
