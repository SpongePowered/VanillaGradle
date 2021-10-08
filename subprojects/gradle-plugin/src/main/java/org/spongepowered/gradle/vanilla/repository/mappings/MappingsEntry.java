package org.spongepowered.gradle.vanilla.repository.mappings;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.provider.Property;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.ObfMappingsEntry;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MappingsEntry implements Named {
    protected final Project project;
    protected final MinecraftExtension extension;
    private final String name;
    private final Property<String> format;
    private @Nullable Configuration configuration;
    private @Nullable Dependency dependency;
    private final Property<String> parent;
    private final Property<Boolean> inverse;
    private final Map<String, SoftReference<MappingSet>> convertFromCache = new ConcurrentHashMap<>();

    public MappingsEntry(Project project, MinecraftExtension extension, String name) {
        this.project = project;
        this.extension = extension;
        this.name = name;
        this.format = project.getObjects().property(String.class);
        this.parent = project.getObjects().property(String.class);
        this.inverse = project.getObjects().property(Boolean.class).convention(false);
    }

    @Override
    public String getName() {
        return name;
    }

    public @Nullable Property<String> format() {
        return format;
    }
    public void format(MappingFormat<@NonNull ?> format) {
        format(format.getName());
    }
    public void format(String format) {
        this.format.set(format);
    }

    public @Nullable Object dependency() {
        return dependency;
    }
    public void dependency(Object dependencyNotation) {
        if (this.dependency != null) {
            throw new IllegalStateException("MappingsEntry.dependency(Object) called twice");
        }
        this.dependency = project.getDependencies().create(dependencyNotation);
        this.configuration = project.getConfigurations().detachedConfiguration(this.dependency);
    }

    public Property<@Nullable String> parent() {
        return parent;
    }
    public void parent(MappingsEntry parent) {
        parent(parent.getName());
    }
    public void parent(String parent) {
        this.parent.set(parent);
    }

    public Property<Boolean> isInverse() {
        return inverse;
    }
    public void invert() {
        inverse(true);
    }
    public void inverse(boolean isInverse) {
        inverse.set(isInverse);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MappingsEntry && ((MappingsEntry) other).name.equals(this.name);
    }

    public final MappingSet convertFrom(
            String otherMappingsName,
            MinecraftResolver.Context context,
            MinecraftResolver.MinecraftEnvironment environment,
            MinecraftPlatform platform,
            ArtifactModifier.SharedArtifactSupplier sharedArtifactSupplier
    ) throws IOException {
        SoftReference<MappingSet> ref = convertFromCache.get(otherMappingsName);
        if (ref != null) {
            @Nullable MappingSet entry = ref.get();
            if (entry != null) {
                return entry;
            }
        }

        MappingSet resolved;
        if (otherMappingsName.equals(getName())) {
            resolved = MappingSet.create();
        } else if (otherMappingsName.equals(ObfMappingsEntry.NAME)) {
            resolved = resolve(context, environment, platform, sharedArtifactSupplier);
        } else {
            MappingsEntry otherMappings = extension.getMappings().getByName(otherMappingsName);
            resolved = otherMappings.resolve(context, environment, platform, sharedArtifactSupplier)
                    .reverse().merge(resolve(context, environment, platform, sharedArtifactSupplier));
        }
        convertFromCache.put(otherMappingsName, new SoftReference<>(resolved));
        return resolved;
    }

    private MappingSet resolve(
            MinecraftResolver.Context context,
            MinecraftResolver.MinecraftEnvironment environment,
            MinecraftPlatform platform,
            ArtifactModifier.SharedArtifactSupplier sharedArtifactSupplier
    ) throws IOException {
        return resolve(context, environment, platform, sharedArtifactSupplier, new HashSet<>());
    }

    final MappingSet resolve(
            MinecraftResolver.Context context,
            MinecraftResolver.MinecraftEnvironment environment,
            MinecraftPlatform platform,
            ArtifactModifier.SharedArtifactSupplier sharedArtifactSupplier,
            Set<String> alreadySeen
    ) throws IOException {
        if (!alreadySeen.add(getName())) {
            throw new IllegalStateException("Recursive mapping dependencies for \"" + getName() + "\"");
        }
        MappingSet resolved = doResolve(context, environment, platform, sharedArtifactSupplier, alreadySeen);
        if (this.inverse.get()) {
            resolved = resolved.reverse();
        }
        if (parent.getOrNull() != null) {
            resolved = extension.getMappings().getByName(parent.get()).resolve(context, environment, platform, sharedArtifactSupplier, alreadySeen).merge(resolved);
        }
        return resolved;
    }

    protected <T extends MappingsEntry> MappingSet doResolve(
            MinecraftResolver.Context context,
            MinecraftResolver.MinecraftEnvironment environment,
            MinecraftPlatform platform,
            ArtifactModifier.SharedArtifactSupplier sharedArtifactSupplier,
            Set<String> alreadySeen) throws IOException {
        if (this.dependency == null) {
            throw new IllegalStateException("Mappings entry \"" + getName() + "\" of format \"" + format.get() + "\" must have a dependency");
        }
        assert this.configuration != null;
        @SuppressWarnings("unchecked")
        MappingFormat<T> mappingFormat = (MappingFormat<T>) extension.getMappingFormats().getByName(format.get());
        if (!mappingFormat.entryType().isInstance(this)) {
            throw new IllegalStateException("Mappings entry \"" + getName() + "\" of type \"" + getClass().getName() + "\" is not compatible with mapping format \"" + format.get() + "\"");
        }
        Set<File> resolvedFiles = CompletableFuture.supplyAsync(this.configuration::resolve, context.syncExecutor()).join();
        if (resolvedFiles.size() != 1) {
            throw new IllegalStateException("Mappings entry \"" + getName() + "\" did not resolve to exactly 1 file");
        }
        Path resolvedFile = resolvedFiles.iterator().next().toPath();
        return mappingFormat.read(resolvedFile, mappingFormat.entryType().cast(this), context);
    }

    public String computeStateKey(boolean isFrom) {
        final MessageDigest digest = HashAlgorithm.SHA1.digest();
        computeHash(digest);
        return HashAlgorithm.toHexString(digest.digest());
    }

    protected void computeHash(MessageDigest digest) {
        digest.update((byte) 0);
        digest.update(getName().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 1);
        digest.update(format.get().getBytes(StandardCharsets.UTF_8));
        if (dependency != null) {
            digest.update((byte) 2);
            if (dependency instanceof ModuleDependency) {
                if (dependency.getGroup() != null) {
                    digest.update(dependency.getGroup().getBytes(StandardCharsets.UTF_8));
                }
                digest.update((":" + dependency.getName()).getBytes(StandardCharsets.UTF_8));
                if (dependency.getVersion() != null) {
                    digest.update((":" + dependency.getVersion()).getBytes(StandardCharsets.UTF_8));
                }
            } else if (dependency instanceof FileCollectionDependency) {
                for (File file : ((FileCollectionDependency) dependency).getFiles()) {
                    try (final InputStream is = new FileInputStream(file)) {
                        final byte[] buf = new byte[4096];
                        int read;
                        while ((read = is.read(buf)) != -1) {
                            digest.update(buf, 0, read);
                        }
                    } catch (final IOException ex) {
                        // ignore, will show up when we try to actually read the mappings
                    }
                }
            } else {
                byte[] bytes = new byte[32];
                new Random().nextBytes(bytes);
                digest.update(bytes);
            }
        }
        if (inverse.get()) {
            digest.update((byte) 3);
        }
        if (parent.getOrNull() != null) {
            digest.update((byte) 4);
            extension.getMappings().getByName(parent.get()).computeHash(digest);
        }
    }
}
