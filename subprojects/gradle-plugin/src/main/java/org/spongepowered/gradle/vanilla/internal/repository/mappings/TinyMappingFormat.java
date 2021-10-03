package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingFormat;
import org.spongepowered.gradle.vanilla.repository.mappings.TinyMappingsEntry;

import java.io.IOException;
import java.nio.file.Path;

public class TinyMappingFormat extends MappingFormat<@NonNull TinyMappingsEntry> {
    public TinyMappingFormat() {
        super(TinyMappingsEntry.class);
    }

    @Override
    public @NonNull String getName() {
        return "tiny";
    }

    @Override
    public @NonNull MappingSet read(@NonNull Path file, @NonNull TinyMappingsEntry entry) throws IOException {
        MappingSet mappings = MappingSet.create();
        net.fabricmc.lorenztiny.TinyMappingFormat.DETECT.read(mappings, file, entry.from().get(), entry.to().get());
        return mappings;
    }
}
