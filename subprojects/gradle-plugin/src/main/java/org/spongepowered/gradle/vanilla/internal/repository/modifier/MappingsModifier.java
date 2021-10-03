package org.spongepowered.gradle.vanilla.internal.repository.modifier;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.gradle.vanilla.internal.resolver.AsyncUtils;
import org.spongepowered.gradle.vanilla.internal.transformer.AtlasTransformers;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

public class MappingsModifier implements ArtifactModifier {
    private static final String KEY = "map"; // custom mapped

    private final MappingsEntry mappings;
    private @Nullable String stateKey;

    public MappingsModifier(final MappingsEntry mappings) {
        this.mappings = mappings;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String stateKey() {
        if (stateKey == null) {
            return this.stateKey = mappings.computeStateKey();
        }
        return stateKey;
    }

    @Override
    public CompletableFuture<AtlasPopulator> providePopulator(MinecraftResolver.Context context) {
        return AsyncUtils.failableFuture(() -> (atlasContext, result, side, sharedArtifactProvider) -> {
            final MappingSet mappings;
            try {
                mappings = this.mappings.resolve(context, result, side, sharedArtifactProvider);
            } catch (IOException e) {
                throw new UncheckedIOException("An exception occurred while trying to read mappings", e);
            }
            return AtlasTransformers.remap(mappings, atlasContext.inheritanceProvider());
        }, context.executor());
    }

    @Override
    public boolean requiresLocalStorage() {
        return false;
    }
}
