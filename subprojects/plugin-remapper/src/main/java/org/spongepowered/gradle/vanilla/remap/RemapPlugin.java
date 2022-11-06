package org.spongepowered.gradle.vanilla.remap;

import net.kyori.mammoth.ProjectPlugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;
import org.jetbrains.annotations.NotNull;

public class RemapPlugin implements ProjectPlugin {

    @Override
    public void apply(
        final @NotNull Project project,
        final @NotNull PluginContainer plugins,
        final @NotNull ExtensionContainer extensions,
        final @NotNull TaskContainer tasks
    ) {
        // add extension
        // OUTGOING
        // extension can create remapped variants of any existing outgoing configuration
        // todo: add these to an existing software component?

        // INCOMING
        // an artifact transform?

        // DURING RESOLUTION
        // ad-hoc create classpath + argument additions
    }
}
