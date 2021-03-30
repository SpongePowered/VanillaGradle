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
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public abstract class DisplayMinecraftVersionsTask extends DefaultTask {

    @Input
    public abstract ListProperty<VersionDescriptor> getVersions();

    @TaskAction
    public void execute() {
        // It is ugly to hardcode the bottom limit but if we don't we'll have to download EACH VERSION to know what we can target!
        final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        final Date earliestDate = Date.from(Instant.from(formatter.parse(Constants.FIRST_TARGETABLE_RELEASE_TIMESTAMP)));

        for (final VersionDescriptor version : this.getVersions().get()) {
            final Date versionDate = Date.from(version.releaseTime().toInstant());
            if (versionDate.before(earliestDate)) {
                continue;
            }
            this.getLogger().lifecycle(version.id());
        }

        this.getLogger().lifecycle(Constants.OUT_OF_BAND_RELEASE);
    }
}
