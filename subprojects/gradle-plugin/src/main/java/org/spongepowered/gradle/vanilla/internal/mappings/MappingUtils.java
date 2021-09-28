/*
 * This file is part of VanillaGradle, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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

        if (type == MappingType.JAR || type == MappingType.ZIP) {
            // We need to find the mappings. Find the first valid mapping file we can.
            try {
                fileSystem = FileSystems.newFileSystem(mappingsFile, (ClassLoader) null);
                mappingsFile = fileSystem.getPath("/mappings/mappings.tiny");

                if (!Files.isRegularFile(mappingsFile)) {
                    mappingsFile = fileSystem.getPath("/parchment.json");

                    if (!Files.isRegularFile(mappingsFile)) {
                        throw new GradleException("Failed to read mappings from " + mappingsFile);
                    }
                }
            } catch (IOException ex) {
                throw new GradleException("Failed to read mappings from " + mappingsFile, ex);
            }
        }

        type = getMappingType(mappingsFile);
        switch (type) {
            case PROGUARD: {
                try (BufferedReader reader = Files.newBufferedReader(mappingsFile, StandardCharsets.UTF_8)) {
                    final ProGuardReader proguard = new ProGuardReader(reader);
                    proguard.read(mappings);
                    mappings = mappings.reverse(); // Flip from named -> obf to named -> obf
                } catch (IOException ex) {
                    throw new GradleException("Failed to read mappings from " + mappingsFile, ex);
                }
                break;
            }

            case TINY: {
                try {
                    mappings = TinyMappingFormat.DETECT.read(mappingsFile, "official", "named");
                } catch (IOException ex) {
                    throw new GradleException("Failed to read mappings from " + mappingsFile, ex);
                }
                break;
            }

            case SRG: {
                final MappingSet finalMappings = mappings;
                read(mappingsFile, (reader) -> {
                    final SrgReader srg = new SrgReader(reader);
                    srg.read(finalMappings);
                });
                break;
            }

            case TSRG: {
                final MappingSet finalMappings = mappings;
                read(mappingsFile, (reader) -> {
                    final TSrgReader tsrg = new TSrgReader(reader);
                    tsrg.read(finalMappings);
                });
                break;
            }

            case PARCHMENT: {
                final MappingSet finalMappings = mappings;

                read(mappingsFile, (reader) -> {
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
        return getMappingType(mappingsFile.toString());
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
