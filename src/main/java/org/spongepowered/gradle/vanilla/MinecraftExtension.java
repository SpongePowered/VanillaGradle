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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Property;
import org.spongepowered.gradle.vanilla.runs.RunConfigurationContainer;

/**
 * Properties that can configure how VanillaGradle applies minecraft
 */
public interface MinecraftExtension {

    /**
     * Get whether repositories should be injected into the build.
     *
     * <p>For users who run their own proxying repositories or who want to
     * manage repositories in another way, this property can be disabled.
     * However, to function VanillaGradle must be able to resolve artifacts
     * that are present in the following repositories:</p>
     * <ul>
     *     <li>Maven Central</li>
     *     <li><a href="https://libraries.minecraft.net/">https://libraries.minecraft.net</a></li>
     *     <li><a href="https://files.minecraftforge.net/maven/">https://files.minecraftforge.net/maven/</a></li>
     * </ul>
     *
     * <p>This list is subject to change in any feature release without that
     * release being marked as "breaking".</p>
     *
     * @return the inject repositories property
     */
    Property<Boolean> injectRepositories();

    /**
     * Set whether standard repositories should be added to the project.
     *
     * @param injectRepositories whether repositories should be injected
     * @see #injectRepositories() for a list of repositories that are injected
     */
    void injectRepositories(boolean injectRepositories);

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
     * Apply an access widener to the project.
     *
     * @param file any file that can be passed to {@link Project#file(Object)}
     */
    void accessWidener(Object file);

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

    /**
     * Create a dependency that can be added to any configuration that needs
     * Minecraft.
     *
     * @return a dependency containing a Minecraft distribution of the chosen
     *     {@link #platform() platform} and {@link #version() version}.
     */
    Dependency minecraftDependency();

}
