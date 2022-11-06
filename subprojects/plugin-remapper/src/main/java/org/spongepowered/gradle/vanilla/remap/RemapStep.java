package org.spongepowered.gradle.vanilla.remap;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * A transformation to be run as part of the binary remap step.
 *
 * @since 0.2.1
 */
public interface RemapStep {

    /**
     * Classpath elements to add to the remapper classpath.
     *
     * @return the classpath file collection
     * @since 0.2.1
     */
    @InputFiles
    ConfigurableFileCollection getClasspath();

    /**
     * Get a list of argument providers to contribute to the remapper
     * command line.
     *
     * @return the argument providers
     * @since 0.2.1
     */
    @Nested
    ListProperty<CommandLineArgumentProvider> getArgumentProviders();


    interface ForgeAutoRenamingToolStep extends RemapStep {

    }

}
