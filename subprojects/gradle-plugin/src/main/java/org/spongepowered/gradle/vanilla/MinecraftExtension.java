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
package org.spongepowered.gradle.vanilla;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.spongepowered.gradle.vanilla.repository.MappingsBuilder;
import org.spongepowered.gradle.vanilla.repository.MappingsReader;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftRepositoryExtension;
import org.spongepowered.gradle.vanilla.runs.RunConfigurationContainer;

/**
 * Properties that can configure how VanillaGradle applies Minecraft to the
 * current project.
 */
public interface MinecraftExtension extends MinecraftRepositoryExtension {

    /**
     * Get a property pointing to the version of Minecraft to prepare.
     *
     * <p>Values of the property are unvalidated!</p>
     *
     * <p>There is no default value.</p>
     *
     * @return the version
     */
    Property<String> version();

    /**
     * Set the version of Minecraft to prepare.
     *
     * <p>This version is only validated when the Minecraft development
     * environment is prepared.</p>
     *
     * @param version the minecraft version
     */
    void version(String version);

    /**
     * Use a version derived from a provided file.
     *
     * <p>This allows building against Minecraft versions that may not be
     * available in the ordinary version list, such as combat snapshots.</p>
     *
     * @param versionFile a file-like object that can be handled
     *     by {@link Project#file(Object)}
     */
    void injectedVersion(Object versionFile);

    /**
     * Set the version of Minecraft used to the latest release.
     *
     * <p>This should only be used in development workspaces or for
     * analysis tools.</p>
     */
    void latestRelease();

    /**
     * Set the version of Minecraft used to the latest snapshot.
     *
     * <p>This should only be used in development workspaces or for
     * analysis tools.</p>
     */
    void latestSnapshot();

    /**
     * Get a property pointing to the Minecraft platform being prepared.
     *
     * <p><b>Default:</b> {@code JOINED}</p>
     *
     * @return the platform property
     */
    Property<MinecraftPlatform> platform();

    /**
     * Set the platform/environment of Minecraft to prepare.
     *
     * @param platform the platform
     */
    void platform(MinecraftPlatform platform);

    /**
     * Apply access wideners to the project.
     *
     * <p>Access wideners can only be added before the first time a Minecraft
     * dependency is resolved.</p>
     *
     * @param file any file that can be passed to {@link Project#file(Object)}
     */
    void accessWideners(Object... file);

    Property<Boolean> useOfficialMappings();

    void useOfficialMappings(boolean useOfficialMappings);

    ListProperty<MappingsReader> mappingsReaders();

    void mappingsReader(MappingsReader... readers);

    MappingsBuilder mappings();

    void mappings(Action<MappingsBuilder> configure);

    void mappings(Closure<MappingsBuilder> configureClosure);

    /**
     * Get run configurations configured for this project.
     *
     * <p>Every run configuration will automatically have Minecraft on its classpath.</p>
     *
     * @return the run configuration.
     */
    RunConfigurationContainer getRuns();

    /**
     * Operate on the available run configurations.
     *
     * @param run a closure operating on the run configuration container
     */
    @SuppressWarnings("rawtypes")
    void runs(@DelegatesTo(value = RunConfigurationContainer.class, strategy = Closure.DELEGATE_FIRST) Closure run);

    /**
     * Operate on the available run configurations.
     *
     * @param run an action operating on the run configuration container
     */
    void runs(Action<RunConfigurationContainer> run);

}
