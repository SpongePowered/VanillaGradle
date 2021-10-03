package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.Project;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.internal.model.Download;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;
import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class OfficialMappingsEntry extends MappingsEntry {
    public static final String NAME = "official";
    private boolean hasInitialized = false;

    public OfficialMappingsEntry(Project project, MinecraftExtension extension) {
        super(project, extension, NAME);
        format(ProGuardMappingFormat.NAME);
        hasInitialized = true;
    }

    @Override
    public void format(@NonNull String format) {
        if (hasInitialized) {
            throw new IllegalStateException("Cannot modify the format of \"" + NAME + "\"");
        } else {
            super.format(format);
        }
    }

    @Override
    public void dependency(@Nullable Object dependencyNotation) {
        throw new IllegalStateException("Cannot modify the dependency of \"" + NAME + "\"");
    }

    @Override
    public void parent(@Nullable String parent) {
        throw new IllegalStateException("Cannot modify the parent of \"" + NAME + "\"");
    }

    @Override
    public void inverse(boolean isInverse) {
        throw new IllegalStateException("Cannot invert \"" + NAME + "\"");
    }

    @Override
    protected <T extends MappingsEntry> @NonNull MappingSet doResolve(
            MinecraftResolver.@NonNull Context context,
            MinecraftResolver.@NonNull MinecraftEnvironment environment,
            @NonNull MinecraftPlatform platform,
            ArtifactModifier.@NonNull SharedArtifactSupplier sharedArtifactSupplier,
            @NonNull Set<String> alreadySeen) {
        @SuppressWarnings("unchecked")
        CompletableFuture<MappingSet>[] mappingsFutures = platform.activeSides().stream().map(side -> {
            Download mappingsDownload = environment.metadata().requireDownload(side.mappingsArtifact());
            return context.downloader().downloadAndValidate(
                    mappingsDownload.url(),
                    sharedArtifactSupplier.supply(side.name().toLowerCase(Locale.ROOT) + "_m-obf", "mappings", "txt"),
                    HashAlgorithm.SHA1,
                    mappingsDownload.sha1()
            ).thenApplyAsync(downloadResult -> {
                if (!downloadResult.isPresent()) {
                    throw new IllegalArgumentException("No mappings were available for Minecraft " + environment.metadata().id() + "side " + side.name()
                            + "! Official mappings are only available for releases 1.14.4 and newer.");
                }
                MappingSet mappings = MappingSet.create();
                try (ProGuardReader reader = new ProGuardReader(Files.newBufferedReader(downloadResult.get()))) {
                    reader.read(mappings);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
                return mappings.reverse(); // proguard mappings are backwards
            }, context.executor());
        }).toArray(CompletableFuture[]::new);
        CompletableFuture<MappingSet> mappingFuture = CompletableFuture.allOf(mappingsFutures)
                .thenApplyAsync($ -> Arrays.stream(mappingsFutures).map(CompletableFuture::join).reduce(MappingSet::merge).orElseGet(MappingSet::create));


        return mappingFuture.join();
    }

    @Override
    public @NonNull String computeStateKey() {
        return "";
    }
}
