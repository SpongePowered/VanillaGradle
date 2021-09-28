package org.spongepowered.gradle.vanilla.internal.mappings;

/**
 * Represents the possible MappingType's supported.
 */
public enum MappingType {
    PROGUARD("txt"),
    SRG(".srg"),
    TSRG(".tsrg"),
    TINY(".tiny"),
    PARCHMENT(".json"),
    JAR(".jar"),
    ZIP(".zip"),
    EMPTY(null);

    public final String fileExtension;

    MappingType(String fileExtension) {
        this.fileExtension = fileExtension;
    }
}
