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
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkerExecutor;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.MinecraftExtensionImpl;
import org.spongepowered.gradle.vanilla.internal.repository.MinecraftProviderService;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.AssociatedResolutionFlags;
import org.spongepowered.gradle.vanilla.internal.worker.JarDecompileWorker;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.resolver.ResolutionResult;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

public abstract class DecompileJarTask extends DefaultTask {

    private static final ReentrantLock DECOMPILE_LOCK = new ReentrantLock();

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

    /**
     * Extra arguments to pass to fernflower, to override VanillaGradle's defaults.
     *
     * @return extra arguments
     */
    @Nested
    @Optional
    public abstract MapProperty<String, String> getExtraFernFlowerArgs();

    @Input
    @Optional
    @Option(option = "force", description = "Whether to decompile again, even if an input file already exists")
    public abstract Property<Boolean> getForced();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void execute() {
        // TODO: get rid of these project references... somehow
        // TODO: also find a less hacky way to ensure we don't acquire a Project lock before a task executing on a different thread can resolve the
        // dependencies necessary (mergetool, AW, etc)
        DecompileJarTask.DECOMPILE_LOCK.lock(); // gradle is super picky about how we resolve configurations, so this lock is a hack fix...
        final CompletableFuture<ResolutionResult<Path>> resultFuture;
        try {
            final MinecraftProviderService minecraftProvider = this.getMinecraftProvider().get();
            final Set<ArtifactModifier> modifiers =
                ((MinecraftExtensionImpl) this.getProject().getExtensions().getByType(MinecraftExtension.class)).modifiers();

            minecraftProvider.primeResolver(this.getProject(), modifiers);
            final Set<AssociatedResolutionFlags> flags = EnumSet.of(AssociatedResolutionFlags.MODIFIES_ORIGINAL);
            if (this.getForced().getOrElse(false)) {
                flags.add(AssociatedResolutionFlags.FORCE_REGENERATE);
            }
            resultFuture = minecraftProvider.resolver().produceAssociatedArtifact(
                this.getMinecraftPlatform().get(),
                this.getMinecraftVersion().get(),
                modifiers,
                "sources",
                flags,
                (env, output) -> {
                    final long totalSystemMemoryBytes =
                        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize() / (1024L * 1024L);
                    return CompletableFuture.runAsync(() -> {
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
                            parameters.getExtraArgs().set(this.getExtraFernFlowerArgs().orElse(Collections.emptyMap()));
                            parameters.getInputJar().set(env.jar().toFile()); // Use the temporary jar
                            parameters.getOutputJar().set(output.toFile());
                        });
                        this.getWorkerExecutor().await();
                    }, ((MinecraftResolver.Context) minecraftProvider.resolver()).syncExecutor());
                }
            );

            try {
                final ResolutionResult<Path> result = minecraftProvider.resolver().processSyncTasksUntilComplete(resultFuture);
                this.setDidWork(!result.upToDate());
            } catch (final ExecutionException ex) {
                throw new GradleException("Failed to decompile " + this.getMinecraftVersion().get(), ex.getCause());
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new GradleException("Interrupted");
            }
        } finally {
            DecompileJarTask.DECOMPILE_LOCK.unlock();
        }
    }

}
