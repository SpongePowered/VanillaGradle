package org.spongepowered.gradle.vanilla.internal.repository.modifier;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.spongepowered.gradle.vanilla.internal.model.Download;
import org.spongepowered.gradle.vanilla.internal.resolver.AsyncUtils;
import org.spongepowered.gradle.vanilla.internal.transformer.AtlasTransformers;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class OfficialMappingsModifier implements ArtifactModifier {
    private static final String KEY = "map";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String stateKey() {
        return "";
    }

    @Override
    public CompletableFuture<AtlasPopulator> providePopulator(MinecraftResolver.Context context) {
        return AsyncUtils.failableFuture(() -> (atlasContext, result, platform, sharedArtifactProvider) -> {
            @SuppressWarnings("unchecked")
            CompletableFuture<MappingSet>[] mappingsFutures = platform.activeSides().stream().map(side -> {
                Download mappingsDownload = result.metadata().requireDownload(side.mappingsArtifact());
                return context.downloader().downloadAndValidate(
                        mappingsDownload.url(),
                        sharedArtifactProvider.supply(side.name().toLowerCase(Locale.ROOT) + "_m-obf", "mappings", "txt"),
                        HashAlgorithm.SHA1,
                        mappingsDownload.sha1()
                ).thenApplyAsync(downloadResult -> {
                    if (!downloadResult.isPresent()) {
                        throw new IllegalArgumentException("No mappings were available for Minecraft " + result.metadata().id() + "side " + side.name()
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


            MappingSet mappings = mappingFuture.join();
            return AtlasTransformers.remap(mappings, atlasContext.inheritanceProvider());
        }, context.executor());
    }

    @Override
    public boolean requiresLocalStorage() {
        return false;
    }
}
