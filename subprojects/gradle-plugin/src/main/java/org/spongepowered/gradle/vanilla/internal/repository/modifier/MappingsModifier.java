package org.spongepowered.gradle.vanilla.internal.repository.modifier;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.gradle.vanilla.internal.resolver.AsyncUtils;
import org.spongepowered.gradle.vanilla.internal.transformer.AtlasTransformers;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

public class MappingsModifier implements ArtifactModifier {
    private static final String KEY = "map"; // custom mapped

    private final MappingsContainer mappings;
    private final String from;
    private final String to;
    private @Nullable String stateKey;

    public MappingsModifier(final MappingsContainer mappings, final String from, final String to) {
        this.mappings = mappings;
        this.from = from;
        this.to = to;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String stateKey() {
        if (stateKey == null) {
            this.stateKey = mappings.getByName(from).computeStateKey(true);
            if (!this.stateKey.isEmpty()) {
                this.stateKey += "-";
            }
            this.stateKey += mappings.getByName(to).computeStateKey(false);
        }
        return stateKey;
    }

    @Override
    public CompletableFuture<AtlasPopulator> providePopulator(MinecraftResolver.Context context) {
        return AsyncUtils.failableFuture(() -> (atlasContext, result, side, sharedArtifactProvider) -> {
            final MappingSet mappings;
            try {
                mappings = this.mappings.getByName(to).convertFrom(from, context, result, side, sharedArtifactProvider);
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
