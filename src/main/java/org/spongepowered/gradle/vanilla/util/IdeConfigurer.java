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
package org.spongepowered.gradle.vanilla.util;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.ProjectSettings;

/**
 * Configures different IDEs when applicable
 */
public final class IdeConfigurer {

    /**
     * Get whether this is an idea project import.
     *
     * @return whether this is an IntelliJ project import in progress
     */
    public static boolean isIdeaImport() {
        return Boolean.getBoolean("idea.active");
    }

    /**
     * Applies the specified configuration action to configure IDE projects.
     *
     * <p>This does not apply the IDEs' respective plugins, but will perform
     * actions when those plugins are applied.</p>
     *
     * @param project project to apply to
     * @param toPerform the actions to perform
     */
    public static void apply(final Project project, final IdeImportAction toPerform) {
        project.getPlugins().withType(IdeaExtPlugin.class, plugin -> {
            if (!IdeConfigurer.isIdeaImport()) {
                return;
            }
            final Project rootProject = project.getRootProject();
            if (project != rootProject) {
                rootProject.getPlugins().apply(IdeaExtPlugin.class);
                final IdeaModel model = rootProject.getExtensions().findByType(IdeaModel.class);
                if (model == null || model.getProject() == null) {
                    return;
                }
                final ProjectSettings ideaExt = ((ExtensionAware) model.getProject()).getExtensions().getByType(ProjectSettings.class);
                toPerform.idea(project, model, ideaExt);
            }
        });
        project.getPlugins().withType(EclipsePlugin.class, plugin -> {
            final EclipseModel model = project.getExtensions().findByType(EclipseModel.class);
            if (model == null) {
                return;
            }
            toPerform.eclipse(project, model);
        });
    }

    public interface IdeImportAction {

        /**
         * Configure an IntelliJ project.
         *
         * @param project the project to configure on import
         * @param idea the basic idea gradle extension
         * @param ideaExtension JetBrain's extensions to the base idea model
         */
        void idea(final Project project, final IdeaModel idea, final ProjectSettings ideaExtension);

        /**
         * Configure an eclipse project.
         *
         * @param project the project being imported
         * @param eclipse the eclipse project model to modify
         */
        void eclipse(final Project project, final EclipseModel eclipse);

    }

}
