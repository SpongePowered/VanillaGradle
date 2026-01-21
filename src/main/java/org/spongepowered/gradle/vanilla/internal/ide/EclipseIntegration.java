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
package org.spongepowered.gradle.vanilla.internal.ide;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class EclipseIntegration {

    /**
     * Applies the specified configuration action to configure Eclipse projects.
     *
     * <p>This does not apply the Eclipse plugin, but will perform the action when the plugin is applied.</p>
     *
     * @param project project to apply to
     * @param action the action to perform
     */
    public static void apply(final Project project, final Consumer<EclipseModel> action) {
        project.getPlugins().withType(EclipsePlugin.class, plugin -> {
            final EclipseModel model = project.getExtensions().findByType(EclipseModel.class);
            if (model == null) {
                return;
            }
            action.accept(model);
        });
    }

    /**
     * Executes a task when Eclipse performs a project synchronization.
     *
     * @param project project of the task
     * @param supplier supplier that may provide a task
     */
    public static void addSynchronizationTask(final Project project, final Supplier<Optional<TaskProvider<?>>> supplier) {
        EclipseIntegration.apply(project, (eclipseModel -> supplier.get().ifPresent(eclipseModel::synchronizationTasks)));
    }
}
