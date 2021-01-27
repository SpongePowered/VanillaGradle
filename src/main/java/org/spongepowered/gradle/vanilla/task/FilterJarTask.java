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

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.bundling.Zip;
import org.spongepowered.gradle.vanilla.Constants;

import javax.inject.Inject;

/**
 * Filter forbidden prefixes out of a class.
 */
public abstract class FilterJarTask extends Zip {

    @Input
    public abstract SetProperty<String> getAllowedPackages();

    @Inject
    protected abstract ArchiveOperations getArchiveOps();

    private String[] allowedPackages;

    public FilterJarTask() {
        this.setGroup(Constants.TASK_GROUP);
        this.getArchiveExtension().set("jar");
        this.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        this.include(element -> {
            if (this.allowedPackages == null) {
                this.allowedPackages = this.getAllowedPackages().get().toArray(new String[0]);
            }
            if (element.isDirectory()) {
                return false;
            }

            if (!element.getPath().contains("/")) {
                return true;
            }

            if (!element.getName().endsWith(".class")) {
                return true;
            }

            for (final String pkg : this.allowedPackages) {
                if (element.getPath().startsWith(pkg)) {
                    return true;
                }
            }
            return false;
        });
    }

    public void fromJar(final Object jar) {
        this.from(this.getArchiveOps().zipTree(jar));
    }

}
