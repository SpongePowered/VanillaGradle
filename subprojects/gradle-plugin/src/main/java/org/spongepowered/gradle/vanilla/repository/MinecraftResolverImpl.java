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
package org.spongepowered.gradle.vanilla.repository;

import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.api.SignatureStripperConfig;
import net.minecraftforge.fart.api.SourceFixerConfig;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.GradleException;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.bundler.BundlerMetadata;
import org.spongepowered.gradle.vanilla.internal.model.Download;
import org.spongepowered.gradle.vanilla.internal.model.GroupArtifactVersion;
import org.spongepowered.gradle.vanilla.internal.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.internal.model.VersionManifestRepository;
import org.spongepowered.gradle.vanilla.internal.repository.IvyModuleWriter;
import org.spongepowered.gradle.vanilla.internal.repository.ResolvableTool;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.AssociatedResolutionFlags;
import org.spongepowered.gradle.vanilla.internal.resolver.AsyncUtils;
import org.spongepowered.gradle.vanilla.internal.resolver.FileUtils;
import org.spongepowered.gradle.vanilla.internal.transformer.Transformers;
import org.spongepowered.gradle.vanilla.internal.util.FunctionalUtils;
import org.spongepowered.gradle.vanilla.internal.util.SelfPreferringClassLoader;
import org.spongepowered.gradle.vanilla.resolver.Downloader;
import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;
import org.spongepowered.gradle.vanilla.resolver.ResolutionResult;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

public class MinecraftResolverImpl implements MinecraftResolver, MinecraftResolver.Context {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftResolverImpl.class);
    private final VersionManifestRepository manifests;
    private final Downloader downloader;
    private final ExecutorService executor;
    private final Path privateCache;
    private final Function<ResolvableTool, URL[]> toolResolver;
    private final ConcurrentMap<EnvironmentKey, CompletableFuture<ResolutionResult<MinecraftEnvironment>>> artifacts = new ConcurrentHashMap<>();
    private final ConcurrentMap<EnvironmentKey, CompletableFuture<ResolutionResult<Path>>> associatedArtifacts = new ConcurrentHashMap<>();
    private final boolean forceRefresh;
    private final BlockingQueue<Runnable> syncTasks = new SynchronousQueue<>();
    private final Executor syncExecutor = run -> this.syncTasks.add(run);

    public MinecraftResolverImpl(
        final VersionManifestRepository manifests,
        final Downloader downloader,
        final Path privateCache,
        final ExecutorService executor,
        final Function<ResolvableTool, URL[]> toolResolver,
        final boolean forceRefresh
    ) {
        this.manifests = manifests;
        this.downloader = downloader;
        this.privateCache = privateCache;
        this.executor = executor;
        this.toolResolver = toolResolver;
        this.forceRefresh = forceRefresh;
    }

    @Override
    public VersionManifestRepository versions() {
        return this.manifests;
    }

    @Override
    public Downloader downloader() {
        return this.downloader;
    }

    @Override
    public Executor executor() {
        return this.executor;
    }

    @Override
    public Executor syncExecutor() {
        return this.syncExecutor;
    }

    @Override
    public Supplier<URLClassLoader> classLoaderWithTool(final ResolvableTool tool) {
        final @Nullable Function<ResolvableTool, URL[]> toolResolver = this.toolResolver;
        if (toolResolver == null) {
            throw new IllegalStateException("No tool resolver has been configured to resolve " + tool);
        }
        final URL[] toolUrls = toolResolver.apply(tool);

        // Create a ClassLoader containing the resolved configuration, plus our own code source to be able to access our own classes
        final URL[] classPath = new URL[toolUrls.length + 1];
        classPath[0] = this.getClass().getProtectionDomain().getCodeSource().getLocation();
        System.arraycopy(toolUrls, 0, classPath, 1, toolUrls.length);
        // Use a custom classloader that prefers classes from the child loader
        // This means we cannot
        return AsyncUtils.memoizedSupplier(() -> new SelfPreferringClassLoader(classPath, MinecraftResolverImpl.class.getClassLoader()));
    }

    // remap a single-sided jar
    CompletableFuture<ResolutionResult<MinecraftEnvironment>> provide(final MinecraftPlatform platform, final MinecraftSide side, final String version, final Path outputJar) {
        return this.artifacts.computeIfAbsent(EnvironmentKey.of(platform, version, null), key -> {
            final CompletableFuture<ResolutionResult<VersionDescriptor.Full>> descriptorFuture = this.manifests.fullVersion(key.versionId());
            return descriptorFuture.thenComposeAsync(potentialDescriptor -> {
                if (!potentialDescriptor.isPresent()) {
                    return CompletableFuture.completedFuture(ResolutionResult.notFound());
                }
                final VersionDescriptor.Full descriptor = potentialDescriptor.get();
                final Download jarDownload = descriptor.requireDownload(side.executableArtifact());
                final Download mappingsDownload = descriptor.requireDownload(side.mappingsArtifact());

                // download to temp path
                final String tempJarPath = this.sharedArtifactFileName(platform.artifactId() + "_m-obf_b-bundled", version, null, "jar");
                final String jarPath = this.sharedArtifactFileName(platform.artifactId() + "_m-obf", version, null, "jar");
                final String mappingsPath = this.sharedArtifactFileName(platform.artifactId() + "_m-obf", version, "mappings", "txt");

                final CompletableFuture<ResolutionResult<Path>> jarFuture = this.downloader.downloadAndValidate(
                    jarDownload.url(),
                    tempJarPath,
                    HashAlgorithm.SHA1,
                    jarDownload.sha1()
                );
                final CompletableFuture<ResolutionResult<Path>> mappingsFuture = this.downloader.downloadAndValidate(
                    mappingsDownload.url(),
                    mappingsPath,
                    HashAlgorithm.SHA1,
                    mappingsDownload.sha1()
                );

                return jarFuture.thenCombineAsync(mappingsFuture, (jar, mappingsFile) -> {
                    try {
                        final boolean outputExists = Files.exists(outputJar);
                        final @Nullable BundlerMetadata bundlerMeta = BundlerMetadata.read(jar.get()).orElse(null);
                        if (bundlerMeta != null) {
                            MinecraftResolverImpl.LOGGER.info("Resolved bundler metadata {} from jar at '{}'", bundlerMeta, jar.get());
                        } else {
                            MinecraftResolverImpl.LOGGER.info("No bundler metadata found in jar {}", jar.get());
                        }
                        final Supplier<Set<GroupArtifactVersion>> dependencies = () -> side.dependencies(descriptor, bundlerMeta);
                        if (!this.forceRefresh && jar.upToDate() && mappingsFile.upToDate() && outputExists) {
                            // Our inputs are up-to-date, and the output exists, so we can assume (for now) that the output is up-to-date
                            // Check meta here too, before returning
                            this.writeMetaIfNecessary(platform, potentialDescriptor, dependencies, outputJar.getParent());
                            // todo: eventually, store a hash along with the jar to compare to, for validation
                            return ResolutionResult.result(new MinecraftEnvironmentImpl(platform.artifactId(), outputJar, dependencies, descriptor), true);
                        } else if (!jar.isPresent()) {
                            throw new IllegalArgumentException("No jar was available for Minecraft " + descriptor.id() + "side " + side.name()
                                + "! Are you sure the data file is correct?");
                        } else if (!mappingsFile.isPresent()) {
                            throw new IllegalArgumentException("No mappings were available for Minecraft " + descriptor.id() + "side " + side.name()
                                + "! Official mappings are only available for releases 1.14.4 and newer.");
                        }
                        MinecraftResolverImpl.LOGGER.warn("Preparing Minecraft: Java Edition {} version {}", side, version);
                        this.cleanAssociatedArtifacts(platform, version);

                        final Path outputTmp = Files.createTempDirectory("vanillagradle").resolve("output" + side.name() + ".jar");
                        FileUtils.createDirectoriesSymlinkSafe(outputJar.getParent());

                        // Extract jar
                        final Path extracted = this.downloader.baseDir().resolve(jarPath);
                        side.extractJar(jar.get(), extracted, bundlerMeta);

                        final IMappingFile scratchMappings;
                        try (
                            final InputStream reader = Files.newInputStream(mappingsFile.get())
                        ) {
                            scratchMappings = IMappingFile.load(reader);
                        } catch (final IOException ex) {
                            throw new GradleException("Failed to read mappings from " + mappingsFile, ex);
                        }
                        final IMappingFile mappings = scratchMappings.reverse();

                        final Renamer.Builder renamerBuilder = Renamer.builder();

                        if (bundlerMeta == null && !side.allowedPackages().isEmpty()) {
                            renamerBuilder.add(ctx -> Transformers.filterEntries(side.allowedPackages()));
                        }
                        renamerBuilder.add(Transformer.parameterAnnotationFixerFactory())
                            .add(Transformers.fixLvNames())
                            .add(Transformer.renamerFactory(mappings))
                            .add(Transformer.sourceFixerFactory(SourceFixerConfig.JAVA))
                            .add(Transformer.recordFixerFactory())
                            .add(Transformer.signatureStripperFactory(SignatureStripperConfig.ALL))
                            .add(Transformers.recordSignatureFixer()); // for versions where old PG produced invalid record signatures

                        renamerBuilder.input(extracted.toFile())
                        .output(outputTmp.toFile())
                        .logger(MinecraftResolverImpl.LOGGER::info)
                        // todo: threads
                        // todo: dependencies
                        .build()
                        .run();

                        this.writeMetaIfNecessary(platform, potentialDescriptor, dependencies, outputJar.getParent());
                        FileUtils.atomicMove(outputTmp, outputJar);
                        // not up-to-date, we had to generate the jar
                        MinecraftResolverImpl.LOGGER.warn("Successfully prepared Minecraft: Java Edition {} version {}", side, version);
                        return ResolutionResult.result(new MinecraftEnvironmentImpl(platform.artifactId(), outputJar, dependencies, descriptor), false);
                    } catch (final IOException | XMLStreamException ex) {
                        throw new CompletionException(ex);
                    }
                }, this.executor);
            }, this.executor);
        });
    }

    // prepare the joined artifact
    CompletableFuture<ResolutionResult<MinecraftEnvironment>> provideJoined(
        final CompletableFuture<ResolutionResult<MinecraftEnvironment>> clientFuture,
        final CompletableFuture<ResolutionResult<MinecraftEnvironment>> serverFuture,
        final String version,
        final Path outputJar
    ) {
        return this.artifacts.computeIfAbsent(EnvironmentKey.of(MinecraftPlatform.JOINED, version, null), key -> {
            // To use Gradle's dependency management, we MUST be on a Gradle thread.
            // For now, let's resolve everything ahead-of-time
            // Shouldn't really do this in a `computeIfAbsent`, but oh well... what gradle tells us, we must do
            final CompletableFuture<ResolutionResult<VersionDescriptor.Full>> descriptorFuture = this.manifests.fullVersion(key.versionId());
            final Executable merge = this.prepareChildLoader(ResolvableTool.JAR_MERGE, "org.spongepowered.gradle.vanilla.internal.worker.JarMerger", "execute");

            return descriptorFuture.thenComposeAsync(potentialDescriptor -> clientFuture.thenCombineAsync(serverFuture, (client, server) -> {
                try {
                    if (!potentialDescriptor.isPresent()) {
                        return ResolutionResult.notFound();
                    }
                    final VersionDescriptor.Full descriptor = potentialDescriptor.get();
                    final boolean outputExists = Files.isRegularFile(outputJar);
                    final Supplier<Set<GroupArtifactVersion>> dependencies = () -> MinecraftResolverImpl.mergedDependencies(client.get(), server.get());
                    if (!this.forceRefresh && client.upToDate() && server.upToDate() && outputExists) {
                        // We're up-to-date, give meta a poke and then return without re-executing the jar merge
                        this.writeMetaIfNecessary(
                            MinecraftPlatform.JOINED,
                            potentialDescriptor,
                            () -> MinecraftResolverImpl.mergedDependencies(client.get(), server.get()),
                            outputJar.getParent()
                        );
                        return ResolutionResult.result(new MinecraftEnvironmentImpl(MinecraftPlatform.JOINED.artifactId(), outputJar, dependencies, descriptor), true);
                    }
                    MinecraftResolverImpl.LOGGER.warn("Preparing Minecraft: Java Edition JOINED version {}", version);
                    this.cleanAssociatedArtifacts(MinecraftPlatform.JOINED, version);

                    final Path outputTmp = FileUtils.temporaryPath(outputJar.getParent(), "mergetmp" + version);

                    // apply jar merge worker as a (Path client, Path server, Path merged)
                    merge.execute(client.get().jar(), server.get().jar(), outputTmp);

                    this.writeMetaIfNecessary(MinecraftPlatform.JOINED, potentialDescriptor, dependencies, outputJar.getParent());
                    FileUtils.atomicMove(outputTmp, outputJar);
                    MinecraftResolverImpl.LOGGER.warn("Successfully prepared Minecraft: Java Edition JOINED version {}", version);
                    return ResolutionResult.result(new MinecraftEnvironmentImpl(MinecraftPlatform.JOINED.artifactId(), outputJar, dependencies, descriptor), false);
                } catch (final Exception ex) {
                    throw new CompletionException(ex);
                }
            }, this.executor));
        });
    }

    private static Set<GroupArtifactVersion> mergedDependencies(final MinecraftEnvironment client, final MinecraftEnvironment server) {
        final Set<GroupArtifactVersion> deps = new HashSet<>();
        deps.addAll(client.dependencies());
        deps.addAll(server.dependencies());
        return Collections.unmodifiableSet(deps);
    }

    /**
     * Single-use executable interface
     */
    @FunctionalInterface
    interface Executable {
        <T> T execute(final Object... args) throws Exception;
    }

    private Executable prepareChildLoader(final ResolvableTool tool, final String className, final String methodName) {
        final Supplier<URLClassLoader> loader = this.classLoaderWithTool(tool);
        return new Executable() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T execute(final Object... args) throws Exception {
                try (final URLClassLoader child = loader.get()) {
                    final Class<?> action = Class.forName(className, true, child);
                    for (final Method method : action.getMethods()) {
                        if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())
                            && method.getParameters().length == args.length) {
                            return (T) method.invoke(null, args);
                        }
                    }
                } catch (final InvocationTargetException ex) {
                    if (ex.getCause() != null && ex.getCause() instanceof Exception) {
                        throw (Exception) ex.getCause();
                    } else {
                        throw ex;
                    }
                }
                throw new IllegalStateException("Could not find method matching name " + methodName + " with arguments " + Arrays.toString(args));
            }
        };
    }

    // todo: state storage
    // do we have some sort of sidecar file that we compare?
    // could have things like:
    // - expected jar hash
    // - configuration of mappings providers
    //

    @Override
    public CompletableFuture<ResolutionResult<MinecraftEnvironment>> provide(
        final MinecraftPlatform side, final String version
    ) {
        return this.provide0(side, version);
    }

    private CompletableFuture<ResolutionResult<MinecraftEnvironment>> provide0(final MinecraftPlatform side, final String version) {
        final Path output;
        try {
            output = this.sharedArtifactPath(side.artifactId(), version, null, "jar");
        } catch (final IOException ex) {
            return AsyncUtils.failedFuture(ex);
        }

        // Each platform is responsible for its own up-to-date checks
        return side.resolveMinecraft(this, version, output);
    }

    @Override
    public CompletableFuture<ResolutionResult<MinecraftEnvironment>> provide(
        final MinecraftPlatform side, final String version, final Set<ArtifactModifier> modifiers
    ) {
        final CompletableFuture<ResolutionResult<MinecraftEnvironment>> unmodified = this.provide0(side, version);
        if (modifiers.isEmpty()) { // no modifiers provided, follow the normal path
            return unmodified;
        }

        final String decoratedArtifact = ArtifactModifier.decorateArtifactId(side.artifactId(), modifiers);
        boolean requiresLocalStorage = false;
        // Synchronously compute the modifier populator providers
        @SuppressWarnings({"unchecked", "rawtypes"})
        final CompletableFuture<ArtifactModifier.TransformerProvider>[] populators = new CompletableFuture[modifiers.size()];

        int idx = 0;
        for (final ArtifactModifier modifier : modifiers) {
            requiresLocalStorage |= modifier.requiresLocalStorage();
            populators[idx++] = modifier.providePopulator(this);
        }

        final boolean finalRequiresLocalStorage = requiresLocalStorage;
        return this.artifacts.computeIfAbsent(EnvironmentKey.of(side, version, decoratedArtifact), $ -> unmodified.thenCombineAsync(
            CompletableFuture.allOf(populators),
            (input, popIgnored) -> {
                try {
                    // compute a file name based on the modifiers
                    final Path output = this.artifactPath(
                        finalRequiresLocalStorage ? this.privateCache : this.downloader.baseDir(),
                        decoratedArtifact,
                        version,
                        null,
                        "jar"
                    );
                    if (!this.forceRefresh && input.upToDate() && Files.isRegularFile(output)) {
                        this.writeMetaIfNecessary(side, decoratedArtifact, input.mapIfPresent((upToDate, env) -> env.metadata()), input.get()::dependencies, output.getParent());
                        return ResolutionResult.result(new MinecraftEnvironmentImpl(decoratedArtifact, output, input.get()::dependencies, input.get().metadata()), true);
                    } else {
                        if (!input.isPresent()) {
                            return ResolutionResult.notFound();
                        }

                        final Path outputTmp = Files.createTempDirectory("vanillagradle").resolve("output" + decoratedArtifact + ".jar");
                        FileUtils.createDirectoriesSymlinkSafe(output.getParent());

                        final Renamer.Builder builder = Renamer.builder()
                            .input(input.get().jar().toFile())
                            .output(outputTmp.toFile());

                        for (final CompletableFuture<ArtifactModifier.TransformerProvider> populator : populators) {
                            builder.add(populator.get().provide());
                        }

                        builder.build()
                            .run();

                        FileUtils.atomicMove(outputTmp, output);
                        this.writeMetaIfNecessary(side, decoratedArtifact, input.mapIfPresent((upToDate, env) -> env.metadata()), input.get()::dependencies, output.getParent());
                        return ResolutionResult.result(new MinecraftEnvironmentImpl(decoratedArtifact, output, input.get()::dependencies, input.get().metadata()), false);
                    }
                } catch (final Exception ex) {
                    throw new CompletionException(ex);
                } finally {
                    for (final CompletableFuture<ArtifactModifier.TransformerProvider> populator : populators) {
                        if (!populator.isCompletedExceptionally()) {
                            try {
                                populator.join().close();
                            } catch (final IOException ex) {
                                // ignore, we will continue trying to close every modifier
                            }
                        }
                    }
                }
            }
        ));
    }

    private void cleanAssociatedArtifacts(final MinecraftPlatform platform, final String version) throws IOException {
        final Path baseArtifact = this.sharedArtifactPath(platform.artifactId(), version, null, "jar");
        int errorCount = 0;
        try (final DirectoryStream<Path> siblings = Files.newDirectoryStream(baseArtifact.getParent(), x -> x.getFileName().toString().endsWith(".jar"))) {
            for (final Path file : siblings) {
                if (!file.equals(baseArtifact)) {
                    try {
                        Files.delete(file);
                    } catch (final IOException ex) {
                        errorCount++;
                    }
                }
            }
        }

        if (errorCount > 0) {
            throw new IOException("Failed to delete " + errorCount + " associated artifacts of " + platform + " " + version + "!");
        }
    }

    @Override
    public CompletableFuture<ResolutionResult<Path>> produceAssociatedArtifact(
        final MinecraftPlatform side,
        final String version,
        final Set<ArtifactModifier> modifiers,
        final String id,
        final Set<AssociatedResolutionFlags> flags,
        final BiFunction<MinecraftEnvironment, Path, CompletableFuture<?>> action
    ) {
        // We need to compute our own key to be able to query the map
        final String decoratedArtifact = ArtifactModifier.decorateArtifactId(side.artifactId(), modifiers) + '-' + id;

        // there's nothing yet, it's our time to resolve
        return this.associatedArtifacts.computeIfAbsent(
            EnvironmentKey.of(side, version, decoratedArtifact),
            key -> this.provide(side, version, modifiers).thenComposeAsync(
                envResult -> {
                    if (!envResult.isPresent()) {
                        throw new IllegalStateException("No environment could be found for '" + side + "' version " + version);
                    }
                    final MinecraftEnvironment env = envResult.get();
                    final Path output = env.jar().resolveSibling(env.decoratedArtifactId() + "-" + env.metadata().id() + "-" + id + ".jar");
                    if (this.forceRefresh || !envResult.upToDate() || flags.contains(AssociatedResolutionFlags.FORCE_REGENERATE) || !Files.exists(output)) {
                        final Path tempOutDir;
                        try {
                            tempOutDir = Files.createTempDirectory("vanillagradle-" + env.decoratedArtifactId() + "-" + id);
                        } catch (final IOException ex) {
                            throw new CompletionException(ex);
                        }
                        final Path tempOut = tempOutDir.resolve(id + ".jar");

                        final CompletableFuture<?> actionResult;
                        if (flags.contains(AssociatedResolutionFlags.MODIFIES_ORIGINAL)) {
                            // To safely modify the input, we copy it to a temporary location, then copy back when the action successfully completes
                            final Path tempInput = tempOutDir.resolve("original-to-modify.jar");
                            try {
                                Files.copy(env.jar(), tempInput);
                            } catch (final IOException ex) {
                                throw new CompletionException(ex);
                            }
                            actionResult = action.apply(new MinecraftEnvironmentImpl(env.decoratedArtifactId(), tempInput, env::dependencies, env.metadata()), tempOut)
                                .thenApply(in -> {
                                    try {
                                        FileUtils.atomicMove(tempInput, env.jar());
                                    } catch (final IOException ex) {
                                        throw new CompletionException(ex);
                                    }
                                    return in;
                                });
                        } else {
                            actionResult = action.apply(env, tempOut);
                        }
                        return actionResult.thenApply(in -> {
                            try {
                                FileUtils.atomicMove(tempOut, output);
                            } catch (final IOException ex) {
                                throw new CompletionException(ex);
                            }
                            return ResolutionResult.result(output, false);
                        });
                    } else {
                        return CompletableFuture.completedFuture(ResolutionResult.result(output, true)); // todo: find some better way of checking validity? for ex. when decompiler version changes
                    }
                },
                this.executor()
            )
        );
    }

    @Override
    public <T> T processSyncTasksUntilComplete(final CompletableFuture<T> future) throws InterruptedException, ExecutionException {
        if (future.isDone()) {
            return future.get();
        }

        future.handleAsync(
            (res, err) -> {
                this.syncTasks.add(new CompleteEvaluation(future));
                return res;
            },
            this.executor
        );

        Runnable action;
        for (;;) {
            action = this.syncTasks.take();

            // todo: rethrow exceptions with an ExecutionException
            if (action instanceof CompleteEvaluation) {
                if (((CompleteEvaluation) action).completed == future) {
                    break;
                } else {
                    this.syncTasks.add(action);
                }
            }

            try {
                action.run();
            } catch (final Exception ex) {
                MinecraftResolverImpl.LOGGER.error("Failed to execute synchronous task {} while resolving {}", action, future, ex);
            }
        }

        return future.get();
    }

    private String sharedArtifactFileName(
        final String artifactId,
        final String version,
        final @Nullable String classifier,
        final String extension
    ) {
        return this.artifactFileName(
            artifactId,
            version,
            classifier,
            extension
        );
    }

    private Path sharedArtifactPath(
        final String artifactId,
        final String version,
        final @Nullable String classifier,
        final String extension
    ) throws IOException {
        return this.artifactPath(
            this.downloader.baseDir(),
            artifactId,
            version,
            classifier,
            extension
        );
    }

    private Path artifactPath(
        final Path baseDir,
        final String artifact,
        final String version,
        final @Nullable String classifier,
        final String extension
    ) throws IOException {
        final Path artifactPath = baseDir.resolve(this.artifactFileName(artifact, version, classifier, extension));
        FileUtils.createDirectoriesSymlinkSafe(artifactPath.getParent());
        return artifactPath;
    }

    private String artifactFileName(
        final String artifact,
        final String version,
        final @Nullable String classifier,
        final String extension
    ) {
        final String fileName = classifier == null ? artifact + '-' + version + '.' + extension : artifact + '-' + version + '-' + classifier + '.' + extension;
        return "net/minecraft/" + artifact + '/' + version + '/' + fileName;
    }

    private void writeMetaIfNecessary(
        final MinecraftPlatform platform,
        final ResolutionResult<VersionDescriptor.Full> version,
        final Supplier<Set<GroupArtifactVersion>> dependencies,
        final Path baseDir
    ) throws IOException, XMLStreamException {
        this.writeMetaIfNecessary(
            platform,
            platform.artifactId(),
            version,
            dependencies,
            baseDir
        );
    }

    private void writeMetaIfNecessary(
        final MinecraftPlatform platform,
        final String artifactIdOverride,
        final ResolutionResult<VersionDescriptor.Full> version,
        final Supplier<Set<GroupArtifactVersion>> dependencies,
        final Path baseDir
    ) throws IOException, XMLStreamException {
        if (!version.isPresent()) {
            return; // not found, we can't resolve
        }
        final Path metaFile = baseDir.resolve("ivy-" + version.get().id() + "-vg" + MinecraftResolver.METADATA_VERSION + ".xml");
        if (!version.upToDate() || !Files.exists(metaFile)) {
            FileUtils.createDirectoriesSymlinkSafe(metaFile.getParent());
            final Path metaFileTmp = FileUtils.temporaryPath(metaFile.getParent(), "metadata");
            try (final IvyModuleWriter writer = new IvyModuleWriter(metaFileTmp)) {
                writer.overrideArtifactId(artifactIdOverride)
                    .dependencies(dependencies.get())
                    .dependencies(Constants.INJECTED_DEPENDENCIES)
                    .write(version.get(), platform);
            }
            FileUtils.atomicMove(metaFileTmp, metaFile);
        }
    }

    /**
     * A key used to resolve a specific minecraft version.
     *
     * <p>This ensures that only one future exists for any given minecraft artifact within a certain runtime.</p>
     */
    @Value.Immutable(builder = false)
    interface EnvironmentKey {

        static EnvironmentKey of(final MinecraftPlatform platform, final String versionId, final @Nullable String extra) {
            return new EnvironmentKeyImpl(platform, versionId, extra);
        }

        @Value.Parameter
        MinecraftPlatform platform();

        @Value.Parameter
        String versionId();

        @Value.Parameter
        @Nullable String extra();

    }

    static final class MinecraftEnvironmentImpl implements MinecraftEnvironment {

        private final String decoratedArtifactId;
        private final Path jar;
        private final Supplier<Set<GroupArtifactVersion>> dependencies;
        private final VersionDescriptor.Full metadata;

        MinecraftEnvironmentImpl(final String decoratedArtifactId, final Path jar, final Supplier<Set<GroupArtifactVersion>> dependencies, final VersionDescriptor.Full metadata) {
            this.decoratedArtifactId = decoratedArtifactId;
            this.jar = jar;
            this.dependencies = FunctionalUtils.memoizeSupplier(dependencies);
            this.metadata = metadata;
        }

        @Override
        public String decoratedArtifactId() {
            return this.decoratedArtifactId;
        }

        @Override
        public Path jar() {
            return this.jar;
        }

        @Override
        public Set<GroupArtifactVersion> dependencies() {
            return this.dependencies.get();
        }

        @Override
        public VersionDescriptor.Full metadata() {
            return this.metadata;
        }

    }

    static final class CompleteEvaluation implements Runnable {

        final CompletableFuture<?> completed;

        CompleteEvaluation(final CompletableFuture<?> completed) {
            this.completed = completed;
        }

        @Override
        public void run() {
            // no-op
        }

    }

}
