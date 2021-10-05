package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import net.fabricmc.lorenztiny.TinyMappingFormat;
import org.cadixdev.lorenz.MappingSet;

import java.io.IOException;
import java.nio.file.Path;

public class TinyReaderImpl {
    public static void read(MappingSet mappings, Path file, String from, String to) throws IOException {
        TinyMappingFormat.DETECT.read(mappings, file, from, to);
    }
}
