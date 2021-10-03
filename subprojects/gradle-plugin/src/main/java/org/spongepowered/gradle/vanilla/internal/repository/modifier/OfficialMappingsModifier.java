package org.spongepowered.gradle.vanilla.internal.repository.modifier;

import org.cadixdev.atlas.AtlasTransformerContext;
import org.cadixdev.atlas.jar.JarFile;
import org.cadixdev.atlas.util.CascadingClassProvider;
import org.cadixdev.bombe.asm.analysis.ClassProviderInheritanceProvider;
import org.cadixdev.bombe.asm.jar.ClassProvider;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.model.Download;
import org.spongepowered.gradle.vanilla.internal.resolver.AsyncUtils;
import org.spongepowered.gradle.vanilla.internal.transformer.AtlasTransformers;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
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
        return AsyncUtils.failableFuture(() -> new AtlasPopulator() {
            private JarFile jarFile;

            @Override
            public JarEntryTransformer provide(AtlasTransformerContext atlasContext, MinecraftResolver.MinecraftEnvironment result, MinecraftPlatform platform, SharedArtifactSupplier sharedArtifactProvider) {
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
                        return mappings;
                    }, context.executor());
                }).toArray(CompletableFuture[]::new);
                CompletableFuture<MappingSet> mappingFuture = CompletableFuture.allOf(mappingsFutures)
                        .thenApplyAsync($ -> Arrays.stream(mappingsFutures).map(CompletableFuture::join).reduce(MappingSet::merge).orElseGet(MappingSet::create));


                MappingSet mappings = mappingFuture.join();
                try {
                    jarFile = new JarFile(result.jar());
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not read result jar file", e);
                }
                ArrayList<ClassProvider> providers = new ArrayList<>();
                providers.add(jarFile);
                return AtlasTransformers.remap(mappings, new ClassProviderInheritanceProvider(Constants.ASM_VERSION, new CascadingClassProvider(providers)));
            }

            @Override
            public void close() throws IOException {
                if (jarFile != null) {
                    jarFile.close();
                }
            }
        }, context.executor());
    }

    @Override
    public boolean requiresLocalStorage() {
        return false;
    }
}
