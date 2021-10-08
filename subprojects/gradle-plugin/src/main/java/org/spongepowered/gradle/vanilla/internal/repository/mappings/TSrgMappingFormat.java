package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingFormat;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;

import java.io.IOException;
import java.nio.file.Path;

public class TSrgMappingFormat extends MappingFormat<@NonNull MappingsEntry> {
    public TSrgMappingFormat() {
        super(MappingsEntry.class);
    }

    @Override
    public @NonNull String getName() {
        return "tsrg";
    }

    @Override
    public @NonNull MappingSet read(
            @NonNull Path file,
            @NonNull MappingsEntry entry,
            MinecraftResolver.@NonNull Context context
    ) throws IOException {
        return new org.cadixdev.lorenz.io.srg.tsrg.TSrgMappingFormat().read(file);
    }
}
