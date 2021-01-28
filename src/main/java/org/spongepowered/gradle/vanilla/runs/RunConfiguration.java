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
package org.spongepowered.gradle.vanilla.runs;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.process.CommandLineArgumentProvider;
import org.spongepowered.gradle.vanilla.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * A configuration for executing a Minecraft environment.
 *
 */
public class RunConfiguration implements Named {

    private final String name;
    private transient final ProjectLayout layout;
    private transient final ObjectFactory objects;

    private final ConfigurableFileCollection classpath;
    private final List<String> args = new ArrayList<>();
    private final List<CommandLineArgumentProvider> allArgs = new ArrayList<>();
    private final List<String> jvmArgs = new ArrayList<>();
    private final List<CommandLineArgumentProvider> allJvmArgs = new ArrayList<>();
    private final DirectoryProperty workingDirectory;
    private final Property<String> mainClass;
    private final Property<String> mainModule;
    private final MapProperty<String, String> launcherMetaTokens;
    private final Property<Boolean> requiresAssetsAndNatives;

    @Inject
    public RunConfiguration(final String name, final ProjectLayout layout, final ObjectFactory objects) {
        this.name = name;
        this.layout = layout;
        this.objects = objects;
        this.classpath = objects.fileCollection();
        this.workingDirectory = objects.directoryProperty()
                .convention(layout.getProjectDirectory().dir("run").dir(name));

        // Apply ad-hoc arguments
        this.allArgs.add(new ConstantListProvider(this.args));
        this.allJvmArgs.add(new ConstantListProvider(this.jvmArgs));
        this.mainClass = objects.property(String.class);
        this.mainModule = objects.property(String.class);
        this.launcherMetaTokens = objects.mapProperty(String.class, String.class);
        this.requiresAssetsAndNatives = objects.property(Boolean.class).convention(false);

        // Apply global environment here
        this.launcherMetaTokens.put(Constants.LauncherEnvironmentTokens.LAUNCHER_NAME, Constants.NAME);
        this.launcherMetaTokens.put(Constants.LauncherEnvironmentTokens.LAUNCHER_VERSION, Constants.VERSION);
    }

    /**
     * Get the launcher meta tokens.
     *
     * <p>These tokens are used to substitute values in arguments provided from
     * a Mojang launcher manifest.</p>
     *
     * @return a map of launcher meta tokens
     * @see org.spongepowered.gradle.vanilla.Constants.LauncherEnvironmentTokens for known token names
     */
    public MapProperty<String, String> launcherMetaTokens() {
        return this.launcherMetaTokens;
    }

    /**
     * Operate on the launcher meta tokens.
     *
     * @param action an action to apply to the meta tokens map
     * @see #launcherMetaTokens() for an explanation of what meta tokens are
     */
    public void launcherMetaTokens(final Action<MapProperty<String, String>> action) {
        Objects.requireNonNull(action).execute(this.launcherMetaTokens);
    }

    /**
     * If assets and natives are required, this will make this run configuration
     * depend on the assets and natives collection tasks.
     *
     * @return the assets and natives property
     */
    public Property<Boolean> requiresAssetsAndNatives() {
        return this.requiresAssetsAndNatives;
    }

    /**
     * Get the classpath used to run this game.
     *
     * @return the JVM classpath
     */
    public ConfigurableFileCollection classpath() {
        return this.classpath;
    }

    public List<CommandLineArgumentProvider> allArguments() {
        return this.allArgs;
    }

    public void args(final String... args) {
        Collections.addAll(this.args, args);
    }

    public List<CommandLineArgumentProvider> allJvmArguments() {
        return this.allJvmArgs;
    }

    public void jvmArgs(final String... args) {
        Collections.addAll(this.jvmArgs, args);
    }

    public DirectoryProperty workingDirectory() {
        return this.workingDirectory;
    }

    public Property<String> mainClass() {
        return this.mainClass;
    }

    public Property<String> mainModule() {
        return this.mainModule;
    }

    @Override
    public @Nonnull String getName() {
        return this.name;
    }

    static final class ConstantListProvider implements CommandLineArgumentProvider {
        private final List<String> contents;

        ConstantListProvider(final List<String> contents) {
            this.contents = contents;
        }

        @Override
        public Iterable<String> asArguments() {
            return this.contents == null ? Collections.emptyList() : this.contents;
        }
    }
}
