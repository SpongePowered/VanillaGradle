package org.spongepowered.gradle.vanilla.internal.repository.modifier;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.provider.ListProperty;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.MappingsBuilderImpl;
import org.spongepowered.gradle.vanilla.internal.resolver.AsyncUtils;
import org.spongepowered.gradle.vanilla.internal.transformer.AtlasTransformers;
import org.spongepowered.gradle.vanilla.repository.MappingsReader;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;

import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

public class MappingsModifier implements ArtifactModifier {
    private static final String KEY = "cm"; // custom mapped

    private final MappingsBuilderImpl mappingsBuilder;
    private final ListProperty<MappingsReader> readers;
    private @Nullable String stateKey;

    public MappingsModifier(final MappingsBuilderImpl mappingsBuilder, ListProperty<MappingsReader> readers) {
        this.mappingsBuilder = mappingsBuilder;
        this.readers = readers;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String stateKey() {
        if (stateKey == null) {
            final MessageDigest digest = HashAlgorithm.SHA1.digest();
            mappingsBuilder.computeStateKey(digest);
            return this.stateKey = HashAlgorithm.toHexString(digest.digest());
        }
        return stateKey;
    }

    @Override
    public CompletableFuture<AtlasPopulator> providePopulator(MinecraftResolver.Context context) {
        return AsyncUtils.failableFuture(() -> (atlasContext, result, side, sharedArtifactProvider) -> {
            MappingSet mappings = mappingsBuilder.create(readers.get());
            return AtlasTransformers.remap(mappings, atlasContext.inheritanceProvider());
        }, context.executor());
    }

    @Override
    public boolean requiresLocalStorage() {
        return false;
    }
}
