package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.gradle.api.Project;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;

import java.util.Set;

public class ObfMappingsEntry extends ImmutableMappingsEntry {
    public static final String NAME = "obf";

    public ObfMappingsEntry(Project project, MinecraftExtension extension) {
        super(project, extension, NAME, ProGuardMappingFormat.NAME);
    }

    @Override
    protected <T extends MappingsEntry> @NonNull MappingSet doResolve(
            MinecraftResolver.@NonNull Context context,
            MinecraftResolver.@NonNull MinecraftEnvironment environment,
            @NonNull MinecraftPlatform platform,
            ArtifactModifier.@NonNull SharedArtifactSupplier sharedArtifactSupplier,
            @NonNull Set<String> alreadySeen
    ) {
        return MappingSet.create();
    }
}
