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

import org.cadixdev.atlas.Atlas;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.model.Download;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.model.VersionManifestRepository;
import org.spongepowered.gradle.vanilla.network.Downloader;
import org.spongepowered.gradle.vanilla.network.HashAlgorithm;
import org.spongepowered.gradle.vanilla.transformer.AtlasTransformers;
import org.spongepowered.gradle.vanilla.util.AsyncUtils;
import org.spongepowered.gradle.vanilla.util.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.xml.stream.XMLStreamException;

public class MinecraftResolverImpl implements MinecraftResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftResolverImpl.class);
    private final VersionManifestRepository manifests;
    private final Downloader downloader;
    private final ExecutorService executor;
    private @Nullable Function<ResolvableTool, URL[]> toolResolver;
    private final ConcurrentMap<EnvironmentKey, CompletableFuture<ResolutionResult<MinecraftEnvironment>>> artifacts = new ConcurrentHashMap<>();

    public MinecraftResolverImpl(
        final VersionManifestRepository manifests,
        final Downloader downloader,
        final ExecutorService executor
    ) {
        this.manifests = manifests;
        this.downloader = downloader;
        this.executor = executor;
    }

    public MinecraftResolverImpl setResolver(final Function<ResolvableTool, URL[]> toolResolver) {
        this.toolResolver = toolResolver;
        return this;
    }

    @Override
    public VersionManifestRepository versions() {
        return this.manifests;
    }

    // remap a single-sided jar
    CompletableFuture<ResolutionResult<MinecraftEnvironment>> provide(final MinecraftPlatform platform, final MinecraftSide side, final String version, final Path outputJar) {
        return this.artifacts.computeIfAbsent(EnvironmentKey.of(platform, version, null), key -> {
            final CompletableFuture<ResolutionResult<VersionDescriptor.Full>> descriptorFuture = this.manifests.fullVersion(key.versionId());
            return descriptorFuture.thenComposeAsync(potentialDescriptor -> {
                if (!potentialDescriptor.isPresent()) {
                    throw new InvalidUserDataException("No minecraft version with the id '" + key.versionId() + "' could be found!");
                }
                final VersionDescriptor.Full descriptor = potentialDescriptor.get();
                final Download jarDownload = descriptor.requireDownload(side.executableArtifact());
                final Download mappingsDownload = descriptor.requireDownload(side.mappingsArtifact());

                final Path jarPath;
                final Path mappingsPath;
                try {
                    jarPath = this.artifactPath(platform, "m-obf", version, null, "jar");
                    mappingsPath = this.artifactPath(platform, "m-obf", version, "mappings", "txt");
                } catch (final IOException ex) {
                    return AsyncUtils.failedFuture(ex);
                }

                final CompletableFuture<ResolutionResult<Path>> jarFuture = this.downloader.downloadAndValidate(
                    jarDownload.url(),
                    jarPath,
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
                        if (jar.upToDate() && mappingsFile.upToDate() && Files.exists(outputJar)) {
                            // Our inputs are up-to-date, and the output exists, so we can assume (for now) that the output is up-to-date
                            // Check meta here too, before returning
                            this.writeMetaIfNecessary(platform, potentialDescriptor, outputJar.getParent());
                            // todo: eventually, store a hash along with the jar to compare to, for validation
                            return ResolutionResult.result(new MinecraftEnvironmentImpl(outputJar, descriptor), true);
                        } else if (!jar.isPresent()) {
                            throw new IllegalArgumentException("No jar was available for Minecraft " + descriptor.id() + "side " + side.name()
                                + "! Are you sure the data file is correct?");
                        } else if (!mappingsFile.isPresent()) {
                            throw new IllegalArgumentException("No mappings were available for Minecraft " + descriptor.id() + "side " + side.name()
                                + "! Official mappings are only available for releases 1.14.4 and newer.");
                        }
                        MinecraftResolverImpl.LOGGER.warn("Preparing Minecraft: Java Edition {} version {}", side, version);

                        final Path outputTmp = Files.createTempDirectory("vanillagradle").resolve("output" + side.name() + ".jar");
                        FileUtils.createDirectoriesSymlinkSafe(outputJar.getParent());
                        final MappingSet scratchMappings = MappingSet.create();
                        try (final BufferedReader reader = Files.newBufferedReader(mappingsFile.get(), StandardCharsets.UTF_8)) {
                            final ProGuardReader proguard = new ProGuardReader(reader);
                            proguard.read(scratchMappings);
                        } catch (final IOException ex) {
                            throw new GradleException("Failed to read mappings from " + mappingsFile, ex);
                        }
                        final MappingSet mappings = scratchMappings.reverse();

                        try (final Atlas atlas = new Atlas(this.executor)) {
                            if (!side.allowedPackages().isEmpty()) {
                                atlas.install(ctx -> AtlasTransformers.filterEntries(side.allowedPackages()));
                            }
                            atlas.install(ctx -> AtlasTransformers.stripSignatures());
                            atlas.install(ctx -> AtlasTransformers.remap(mappings, ctx.inheritanceProvider()));

                            atlas.run(jar.get(), outputTmp);
                        }
                        this.writeMetaIfNecessary(platform, potentialDescriptor, outputJar.getParent());
                        FileUtils.atomicMove(outputTmp, outputJar);
                        // not up-to-date, we had to generate the jar
                        MinecraftResolverImpl.LOGGER.warn("Successfully prepared Minecraft: Java Edition {} version {}", side, version);
                        return ResolutionResult.result(new MinecraftEnvironmentImpl(outputJar, descriptor), false);
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
            final Executable merge = this.prepareChildLoader(ResolvableTool.JAR_MERGE, "org.spongepowered.gradle.vanilla.worker.JarMerger", "execute");

            return descriptorFuture.thenComposeAsync(potentialDescriptor -> clientFuture.thenCombineAsync(serverFuture, (client, server) -> {
                try {
                    if (!potentialDescriptor.isPresent()) {
                        return ResolutionResult.notFound();
                    }
                    final VersionDescriptor.Full descriptor = potentialDescriptor.get();
                    if (client.upToDate() && server.upToDate() && Files.isRegularFile(outputJar)) {
                        // We're up-to-date, give meta a poke and then return without re-executing the jar merge
                        this.writeMetaIfNecessary(MinecraftPlatform.JOINED, potentialDescriptor, outputJar.getParent());
                        return ResolutionResult.result(new MinecraftEnvironmentImpl(outputJar, descriptor), true);
                    }
                    MinecraftResolverImpl.LOGGER.warn("Preparing Minecraft: Java Edition JOINED version {}", version);

                    final Path outputTmp = Files.createTempDirectory("vanillagradle").resolve("mergetmp" + version + ".jar");

                    // apply jar merge worker as a (Path client, Path server, Path merged)
                    merge.execute(client.get().jar(), server.get().jar(), outputTmp);

                    this.writeMetaIfNecessary(MinecraftPlatform.JOINED, potentialDescriptor, outputJar.getParent());
                    FileUtils.atomicMove(outputTmp, outputJar);
                    MinecraftResolverImpl.LOGGER.warn("Successfully prepared Minecraft: Java Edition JOINED version {}", version);
                    return ResolutionResult.result(new MinecraftEnvironmentImpl(outputJar, descriptor), false);
                } catch (final Exception ex) {
                    throw new CompletionException(ex);
                }
            }, this.executor));
        });
    }

    /**
     * Single-use executable interface
     */
    @FunctionalInterface
    interface Executable {
        void execute(final Object... args) throws Exception;
    }

    private Executable prepareChildLoader(final ResolvableTool tool, final String className, final String methodName) {
        final @Nullable Function<ResolvableTool, URL[]> toolResolver = this.toolResolver;
        if (toolResolver == null) {
            throw new IllegalStateException("No tool resolver has been configured to resolve " + tool);
        }
        final URL[] toolUrls = toolResolver.apply(tool);

        return args -> {
            // Create a ClassLoader containing the resolved configuration, plus our own code source to be able to access our own classes
            final URL[] classPath = new URL[toolUrls.length + 1];
            classPath[0] = this.getClass().getProtectionDomain().getCodeSource().getLocation();
            System.arraycopy(toolUrls, 0, classPath, 1, toolUrls.length);
            // This has to be the system classloader, since otherwise there are issues with
            // this classloader preferring classes from the parent classloader when the same URL
            // is present in both.
            // TODO: provide a better workaround... would be nice to share MappingSets around
            try (final URLClassLoader child = new URLClassLoader(classPath, ClassLoader.getSystemClassLoader())) {
                final Class<?> action = Class.forName(className, true, child);
                for (final Method method : action.getMethods()) {
                    if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())
                        && method.getParameters().length == args.length) {
                        method.invoke(null, args);
                        return;
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
        final Path output;
        try {
            output = this.artifactPath(side, null, version, null, "jar");
        } catch (final IOException ex) {
            return AsyncUtils.failedFuture(ex);
        }

        // Each platform is responsible for its own up-to-date checks
        return side.resolveMinecraft(this, version, output);
    }

    @Override
    public Path produceAssociatedArtifactSync(
        final MinecraftPlatform side, final String version, final String id, final BiConsumer<MinecraftEnvironment, Path> action
    ) throws Exception {
        final Path output = this.artifactPath(side, null, version, id, "jar");
        final ResolutionResult<MinecraftEnvironment> env = this.provide(side, version).get();
        if (!env.upToDate() || !Files.exists(output)) {
            final Path tempOut = Files.createTempFile("vanillagradle-" + side.artifactId() + "id", ".tmp.jar");

            action.accept(env.get(), tempOut);
            FileUtils.atomicMove(tempOut, output);
        }
        return output; // todo: find some better way of checking validity? for ex. when decompiler version changes
    }

    private Path artifactPath(
        final MinecraftPlatform platform,
        final @Nullable String extraArtifact,
        final String version,
        final @Nullable String classifier,
        final String extension
    ) throws IOException {
        final String artifact = extraArtifact == null ? platform.artifactId() : platform.artifactId() + '_' + extraArtifact;
        final String fileName = classifier == null ? artifact + '-' + version + '.' + extension : artifact + '-' + version + '-' + classifier + '.' + extension;
        final Path directory = this.downloader.baseDir().resolve("net/minecraft").resolve(artifact).resolve(version);
        FileUtils.createDirectoriesSymlinkSafe(directory);
        return directory.resolve(fileName);
    }

    private void writeMetaIfNecessary(
        final MinecraftPlatform platform,
        final ResolutionResult<VersionDescriptor.Full> version,
        final Path baseDir
    ) throws IOException, XMLStreamException {
        if (!version.isPresent()) {
            return; // not found, we can't resolve
        }
        final Path metaFile = baseDir.resolve("ivy-" + version.get().id() + ".xml");
        if (!version.upToDate() || !Files.exists(metaFile)) {
            FileUtils.createDirectoriesSymlinkSafe(metaFile.getParent());
            final Path metaFileTmp = FileUtils.temporaryPath(metaFile.getParent(), "metadata");
            try (final IvyModuleWriter writer = new IvyModuleWriter(metaFileTmp)) {
                writer.write(version.get(), platform);
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

        private final Path jar;
        private final VersionDescriptor.Full metadata;

        MinecraftEnvironmentImpl(final Path jar, final VersionDescriptor.Full metadata) {
            this.jar = jar;
            this.metadata = metadata;
        }

        @Override
        public Path jar() {
            return this.jar;
        }

        @Override
        public VersionDescriptor.Full metadata() {
            return this.metadata;
        }

        @Override
        public CompletableFuture<MinecraftEnvironment> accessWidened(final Path... wideners) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

    }

}
