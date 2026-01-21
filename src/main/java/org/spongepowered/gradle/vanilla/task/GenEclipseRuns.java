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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.spongepowered.gradle.vanilla.internal.runs.EclipseRunConfigurationWriter;
import org.spongepowered.gradle.vanilla.runs.RunConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.stream.XMLStreamException;

public abstract class GenEclipseRuns extends DefaultTask {

    @Input
    public abstract Property<String> getProjectName();

    @InputDirectory
    public abstract DirectoryProperty getProjectDirectory();

    @Input
    @Optional
    public abstract Property<Boolean> getPreserveExisting();

    @Internal // technically wrong, but tells gradle task validation to shove it
    public abstract SetProperty<RunConfiguration> getRunConfigurations();

    @TaskAction
    public void generateRuns() throws XMLStreamException, IOException {
        final Path projectDir = this.getProjectDirectory().get().getAsFile().toPath();
        boolean wroteAny = false;
        for (final RunConfiguration run : this.getRunConfigurations().get()) {
            final Path output = projectDir.resolve(run.getDisplayName().getOrElse(run.getName()) + ".launch");
            if (this.getPreserveExisting().getOrElse(false) && Files.exists(output)) {
                continue;
            }

            try (final EclipseRunConfigurationWriter writer = new EclipseRunConfigurationWriter(output)) {
                writer.projectName(this.getProjectName().get())
                    .write(run);

                run.getWorkingDirectory().get().getAsFile().mkdirs();
            }
            wroteAny = true;
        }

        this.setDidWork(wroteAny);
    }

}
