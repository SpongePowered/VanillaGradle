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

import org.gradle.StartParameter;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.ProjectSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class IdeaIntegration {

    /**
     * Get whether Gradle is being invoked through IntelliJ IDEA.
     *
     * <p>This can be through a project import, or a task execution.</p>
     *
     * @return whether this is an IntelliJ-based invocation
     */
    public static boolean isIdea() {
        return Boolean.getBoolean("idea.active");
    }

    /**
     * Get whether Gradle is being invoked through IntelliJ IDEA project synchronization.
     *
     * @return whether this is an IntelliJ-based synchronization
     */
    public static boolean isIdeaSync() {
        return Boolean.getBoolean("idea.sync.active");
    }

    /**
     * Applies the specified configuration action to configure Idea projects.
     *
     * <p>This does not apply the Idea plugin, but will perform the action when the plugin is applied.</p>
     *
     * @param project project to apply to
     * @param action the action to perform
     */
    public static void apply(final Project project, final BiConsumer<IdeaModel, ProjectSettings> action) {
        project.getPlugins().withType(IdeaExtPlugin.class, plugin -> {
            if (!IdeaIntegration.isIdea()) {
                return;
            }

            // Apply the IDE plugin to the root project
            final Project rootProject = project.getRootProject();
            if (project != rootProject) {
                rootProject.getPlugins().apply(IdeaExtPlugin.class);
            }
            final IdeaModel model = rootProject.getExtensions().findByType(IdeaModel.class);
            if (model == null || model.getProject() == null) {
                return;
            }
            final ProjectSettings ideaExt = ((ExtensionAware) model.getProject()).getExtensions().getByType(ProjectSettings.class);

            // But actually perform the configuration with the subproject context
            action.accept(model, ideaExt);
        });
    }

    /**
     * Executes a task when Idea performs a project synchronization.
     *
     * @param project project of the task
     * @param supplier supplier that may provide a task
     */
    public static void addSynchronizationTask(final Project project, final Supplier<Optional<TaskProvider<?>>> supplier) {
        if (!IdeaIntegration.isIdeaSync()) {
            return;
        }

        project.afterEvaluate(p -> supplier.get().ifPresent(task -> {
            final StartParameter startParameter = project.getGradle().getStartParameter();
            final List<TaskExecutionRequest> taskRequests = new ArrayList<>(startParameter.getTaskRequests());

            taskRequests.add(new DefaultTaskExecutionRequest(Collections.singletonList(":" + project.getName() + ":" + task.getName())));
            startParameter.setTaskRequests(taskRequests);
        }));
    }
}
