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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.process.CommandLineArgumentProvider;
import org.spongepowered.gradle.vanilla.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * A configuration for executing a Minecraft environment.
 *
 */
public class RunConfiguration implements Named {

    private final String name;
    private final ConfigurableFileCollection classpath;
    private final List<String> args = new ArrayList<>();
    private final List<CommandLineArgumentProvider> allArgs = new ArrayList<>();
    private final List<String> jvmArgs = new ArrayList<>();
    private final List<CommandLineArgumentProvider> allJvmArgs = new ArrayList<>();
    private final DirectoryProperty workingDirectory;
    private final Property<String> displayName;
    private final Property<String> mainClass;
    private final Property<String> mainModule;
    private final MapProperty<String, String> parameterTokens;
    private final Property<Boolean> requiresAssetsAndNatives;
    private final Property<SourceSet> ideaSourceSet;
    private final Property<JavaLanguageVersion> targetVersion;

    @Inject
    public RunConfiguration(final String name, final ProjectLayout layout, final ObjectFactory objects) {
        this.name = name;
        this.displayName = objects.property(String.class);

        this.classpath = objects.fileCollection();
        this.workingDirectory = objects.directoryProperty()
                .convention(layout.getProjectDirectory().dir("run").dir(this.name));

        // Apply ad-hoc arguments
        this.allArgs.add(new ConstantListProvider(this.args));
        this.allJvmArgs.add(new ConstantListProvider(this.jvmArgs));
        this.mainClass = objects.property(String.class);
        this.mainModule = objects.property(String.class);
        this.parameterTokens = objects.mapProperty(String.class, String.class);
        this.requiresAssetsAndNatives = objects.property(Boolean.class).convention(false);
        this.ideaSourceSet = objects.property(SourceSet.class);
        this.targetVersion = objects.property(JavaLanguageVersion.class);

        // Apply global environment here
        this.parameterTokens.put(ClientRunParameterTokens.LAUNCHER_NAME, Constants.NAME);
        this.parameterTokens.put(ClientRunParameterTokens.LAUNCHER_VERSION, Constants.VERSION);
    }

    @Input
    @Optional
    public Property<String> getDisplayName() {
        return this.displayName;
    }

    public void displayName(final String displyName) {
        this.displayName.set(displyName);
    }

    /**
     * Get the run parameter tokens.
     *
     * <p>These tokens are used to substitute values in arguments provided from
     * a Mojang launcher manifest.</p>
     *
     * @return a map of launcher meta tokens
     * @see ClientRunParameterTokens for known token names
     */
    @Internal
    public MapProperty<String, String> getParameterTokens() {
        return this.parameterTokens;
    }

    /**
     * Operate on the run parameter tokens.
     *
     * @param action an action to apply to the parameter tokens map
     * @see #getParameterTokens() for an explanation of what parameter tokens are
     */
    public void parameterTokens(final Action<MapProperty<String, String>> action) {
        Objects.requireNonNull(action).execute(this.parameterTokens);
    }

    /**
     * If assets and natives are required, this will make this run configuration
     * depend on the assets and natives collection tasks.
     *
     * @return the assets and natives property
     */
    @Input
    public Property<Boolean> getRequiresAssetsAndNatives() {
        return this.requiresAssetsAndNatives;
    }

    /**
     * Get the classpath used to run this game.
     *
     * @return the JVM classpath
     */
    @Internal
    public ConfigurableFileCollection getClasspath() {
        return this.classpath;
    }

    /**
     * Get the source set to be used when generating IDEA runs.
     *
     * @return the source set to use
     * @since 0.2
     */
    @Input
    @Optional
    public Property<SourceSet> getIdeaRunSourceSet() {
        return this.ideaSourceSet;
    }

    @Internal
    public List<CommandLineArgumentProvider> getAllArgumentProviders() {
        return this.allArgs;
    }

    public void args(final String... args) {
        Collections.addAll(this.args, args);
    }

    public List<String> allArguments() {
        final List<String> values = new ArrayList<>();
        for (final CommandLineArgumentProvider provider : this.allArgs) {
            final Iterable<String> arguments = provider.asArguments();
            if (arguments instanceof Collection<?>) {
                values.addAll((Collection<String>) arguments);
            } else {
                for (final String argument : arguments) {
                    values.add(argument);
                }
            }
        }
        return values;
    }

    @Internal
    public List<CommandLineArgumentProvider> getAllJvmArgumentProviders() {
        return this.allJvmArgs;
    }

    public void jvmArgs(final String... args) {
        Collections.addAll(this.jvmArgs, args);
    }

    public List<String> allJvmArguments() {
        final List<String> values = new ArrayList<>();
        for (final CommandLineArgumentProvider provider : this.allJvmArgs) {
            final Iterable<String> arguments = provider.asArguments();
            if (arguments instanceof Collection<?>) {
                values.addAll((Collection<String>) arguments);
            } else {
                for (final String argument : arguments) {
                    values.add(argument);
                }
            }
        }
        return values;
    }

    @Internal
    public DirectoryProperty getWorkingDirectory() {
        return this.workingDirectory;
    }

    public void workingDirectory(final File directory) {
        this.workingDirectory.set(directory);
    }

    @Input
    public Property<String> getMainClass() {
        return this.mainClass;
    }

    public void mainClass(final String mainClass) {
        this.mainClass.set(mainClass);
    }

    @Input
    @Optional
    public Property<String> getMainModule() {
        return this.mainModule;
    }

    public void mainModule(final String mainModule) {
        this.mainModule.set(mainModule);
    }

    public Property<JavaLanguageVersion> getTargetVersion() {
        return this.targetVersion;
    }

    public void targetVersion(final JavaLanguageVersion version) {
        this.targetVersion.set(version);
    }

    public void targetVersion(final int version) {
        this.targetVersion.set(JavaLanguageVersion.of(version));
    }

    @Override
    @Input
    public @NonNull String getName() {
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
