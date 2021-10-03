package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingFormat;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProGuardMappingFormat extends MappingFormat<@NonNull MappingsEntry> {
    public static final String NAME = "proguard";

    public ProGuardMappingFormat() {
        super(MappingsEntry.class);
    }

    @Override
    public @NonNull String getName() {
        return NAME;
    }

    @Override
    public @NonNull MappingSet read(final @NonNull Path file, final @NonNull MappingsEntry entry) throws IOException {
        final MappingSet scratchMappings = MappingSet.create();
        try (ProGuardReader proguard = new ProGuardReader(Files.newBufferedReader(file))) {
            proguard.read(scratchMappings);
        }
        return scratchMappings.reverse(); // ProGuard mappings are backwards
    }
}
