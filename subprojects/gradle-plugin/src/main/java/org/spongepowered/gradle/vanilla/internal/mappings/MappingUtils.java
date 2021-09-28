package org.spongepowered.gradle.vanilla.internal.mappings;

import net.fabricmc.lorenztiny.TinyMappingFormat;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.cadixdev.lorenz.io.srg.SrgReader;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader;
import org.gradle.api.GradleException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MappingUtils {

    /**
     * Reads mappings into a {@link MappingSet}.
     *
     * @param mappingsFile the location of the file containing mappings
     * @return a {@link MappingSet} containing all mappings provided.
     */
    public static MappingSet readMappings(Path mappingsFile) {
        MappingSet mappings = MappingSet.create();
        MappingType type = getMappingType(mappingsFile);
        FileSystem fileSystem = null; // If a filesystem is created in a jar, we need to close it manually
        AtomicReference<Path> path = new AtomicReference<>(mappingsFile);

        switch (type) {
            case ZIP:
            case JAR: {
                // We need to find the mappings. Find the first valid mapping file we can.
                try {
                    fileSystem = FileSystems.newFileSystem(mappingsFile, (ClassLoader) null);
                    fileSystem.getRootDirectories().forEach(p -> {
                        String name = p.toString();

                        // A bit hacky, but yarn bundle more than mappings in their jars
                        if (name.contains("mappings") || name.contains("parchment")) {
                            path.set(p);
                        }
                    });
                } catch (IOException ex) {
                    throw new GradleException("Failed to read mappings from " + mappingsFile, ex);
                }
            }

            case PROGUARD: {
                try (BufferedReader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
                    final ProGuardReader proguard = new ProGuardReader(reader);
                    proguard.read(mappings);
                    mappings = mappings.reverse(); // Flip from named -> obf to named -> obf
                } catch (IOException ex) {
                    throw new GradleException("Failed to read mappings from " + path, ex);
                }
                break;
            }

            case TINY: {
                try {
                    mappings = TinyMappingFormat.DETECT.read(path.get(), "official", "named");
                } catch (IOException ex) {
                    throw new GradleException("Failed to read mappings from " + mappingsFile, ex);
                }
                break;
            }

            case SRG: {
                final MappingSet finalMappings = mappings;
                read(path.get(), (reader) -> {
                    final SrgReader srg = new SrgReader(reader);
                    srg.read(finalMappings);
                });
                break;
            }

            case TSRG: {
                final MappingSet finalMappings = mappings;
                read(path.get(), (reader) -> {
                    final TSrgReader tsrg = new TSrgReader(reader);
                    tsrg.read(finalMappings);
                });
                break;
            }

            case PARCHMENT: {
                final MappingSet finalMappings = mappings;

                read(path.get(), (reader) -> {
                    //TODO: complete. This is a bit trickier than the others.
                    // We need to first get the mapping set for mojmap, then apply Parchment on top.
                    // But how do we know which mojmap to use? probably could just guess by using the version.
                    final ParchmentReader parchment = new ParchmentReader(reader);
                    parchment.read(finalMappings);
                });
                break;
            }

            case EMPTY:
                throw new GradleException("Unknown mappings type");
        }

        if (fileSystem != null) {
            try {
                fileSystem.close();
            } catch (IOException ex) {
                throw new GradleException("Failed to close jar filesystem");
            }
        }

        return mappings;
    }

    /**
     * Reads a file into a buffered reader and automatically closes it. Also lowers the amount of duplicated code in here.
     */
    private static void read(Path path, Consumer<BufferedReader> consumer) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            consumer.accept(reader);
        } catch (IOException ex) {
            throw new GradleException("Failed to read mappings from " + path, ex);
        }
    }

    /**
     * Returns the {@link MappingType} based off the extension of the file
     *
     * @param mappingsFile The {@link Path} of the mappings
     */
    private static MappingType getMappingType(Path mappingsFile) {
        return getMappingType(mappingsFile.getFileName());
    }

    /**
     * Returns the {@link MappingType} based off the extension of the file
     *
     * @param fileName The name of the file you are checking.
     */
    private static MappingType getMappingType(String fileName) {
        for (MappingType type : MappingType.values()) {
            if (fileName.endsWith(type.fileExtension)) {
                return type;
            }
        }
        return MappingType.EMPTY;
    }
}
