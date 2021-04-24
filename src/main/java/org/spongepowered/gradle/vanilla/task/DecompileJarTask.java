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

import com.sun.management.OperatingSystemMXBean;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkerExecutor;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.MinecraftExtensionImpl;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftProviderService;
import org.spongepowered.gradle.vanilla.repository.ResolutionResult;
import org.spongepowered.gradle.vanilla.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.repository.modifier.AssociatedResolutionFlags;
import org.spongepowered.gradle.vanilla.worker.JarDecompileWorker;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

public abstract class DecompileJarTask extends DefaultTask {

    public DecompileJarTask() {
        this.setGroup(Constants.TASK_GROUP);
    }

    /**
     * Get the classpath used to execute the jar decompile worker.
     *
     * <p>This must contain the {@code net.minecraftforge:forgeflower} library and
     * its dependencies.</p>
     *
     * @return the classpath.
     */
    @Classpath
    public abstract FileCollection getWorkerClasspath();

    public abstract void setWorkerClasspath(final FileCollection collection);

    @Input
    public abstract Property<ArtifactCollection> getInputArtifacts();

    @Input
    public abstract Property<MinecraftPlatform> getMinecraftPlatform();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Internal
    public abstract Property<MinecraftProviderService> getMinecraftProvider();

    @Input
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void execute() throws Exception {
        // TODO: get rid of these project references... somehow
        final Set<ArtifactModifier> modifiers = ((MinecraftExtensionImpl) this.getProject().getExtensions().getByType(MinecraftExtension.class)).modifiers();

        final ResolutionResult<Path> result = this.getMinecraftProvider().get().resolver(this.getProject()).produceAssociatedArtifactSync(
                this.getMinecraftPlatform().get(),
                this.getMinecraftVersion().get(),
                modifiers,
                "sources",
                EnumSet.of(AssociatedResolutionFlags.MODIFIES_ORIGINAL),
                (env, output) -> {
                    // Determine which parts of the configuration are MC, and which are its dependencies
                    final Set<File> dependencies = new HashSet<>();
                    for (final ResolvedArtifactResult artifact : this.getInputArtifacts().get()) {
                        if (artifact.getId() instanceof ModuleComponentArtifactIdentifier) {
                            final ModuleComponentArtifactIdentifier id = (ModuleComponentArtifactIdentifier) artifact.getId();
                            if (id.getComponentIdentifier().getGroup().equals(MinecraftPlatform.GROUP)) {
                                if (env.decoratedArtifactId().equals(id.getComponentIdentifier().getModule())) {
                                    continue;
                                }
                            }
                        }
                        dependencies.add(artifact.getFile());
                    }

                    if (dependencies.isEmpty()) {
                        throw new InvalidUserDataException("No dependencies were found as part of the classpath");
                    }

                    // Execute in an isolated JVM that can access our customized classpath
                    // This actually performs the decompile
                    final long totalSystemMemoryBytes =
                        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize() / (1024L * 1024L);
                    this.getWorkerExecutor().processIsolation(spec -> {
                        spec.forkOptions(options -> {
                            options.setMaxHeapSize(Math.max(totalSystemMemoryBytes / 4, 4096) + "M");
                            // Enable toolchain support
                            if (this.getJavaLauncher().isPresent()) {
                                final JavaLauncher launcher = this.getJavaLauncher().get();
                                options.setExecutable(launcher.getExecutablePath());
                            }
                        });
                        spec.getClasspath().from(this.getWorkerClasspath());
                    }).submit(JarDecompileWorker.class, parameters -> {
                        parameters.getDecompileClasspath().from(dependencies);
                        parameters.getInputJar().set(env.jar().toFile()); // Use the temporary jar
                        parameters.getOutputJar().set(output.toFile());
                    });
                    this.getWorkerExecutor().await();
                }
            );

        this.setDidWork(!result.upToDate());

    }
}
