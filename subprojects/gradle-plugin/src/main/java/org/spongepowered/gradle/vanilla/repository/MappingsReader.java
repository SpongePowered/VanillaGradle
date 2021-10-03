package org.spongepowered.gradle.vanilla.repository;

import org.cadixdev.lorenz.MappingSet;

import java.io.IOException;
import java.nio.file.Path;

public interface MappingsReader {
    String getName();
    MappingSet read(final Path file) throws IOException;
}
