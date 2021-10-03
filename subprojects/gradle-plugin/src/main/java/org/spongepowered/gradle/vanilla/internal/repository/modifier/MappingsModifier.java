package org.spongepowered.gradle.vanilla.internal.repository.modifier;

import org.cadixdev.atlas.AtlasTransformerContext;
import org.cadixdev.atlas.jar.JarFile;
import org.cadixdev.atlas.util.CascadingClassProvider;
import org.cadixdev.bombe.asm.analysis.ClassProviderInheritanceProvider;
import org.cadixdev.bombe.asm.jar.ClassProvider;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.provider.ListProperty;
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.repository.mappings.MappingsBuilderImpl;
import org.spongepowered.gradle.vanilla.internal.resolver.AsyncUtils;
import org.spongepowered.gradle.vanilla.internal.transformer.AtlasTransformers;
import org.spongepowered.gradle.vanilla.repository.MappingsReader;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.resolver.HashAlgorithm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.util.ArrayList;
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
        return AsyncUtils.failableFuture(() -> new AtlasPopulator() {
            private JarFile jarFile;

            @Override
            public JarEntryTransformer provide(AtlasTransformerContext atlasContext, MinecraftResolver.MinecraftEnvironment result, MinecraftPlatform side, SharedArtifactSupplier sharedArtifactProvider) {
                MappingSet mappings = mappingsBuilder.create(readers.get());
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
