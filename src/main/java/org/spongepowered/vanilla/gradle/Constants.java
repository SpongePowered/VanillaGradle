package org.spongepowered.vanilla.gradle;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Constants {

    public static final String NAME = "VanillaGradle";
    public static final String VERSION = Constants.version();
    public static final String API_V2_ENDPOINT = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String TASK_GROUP = "vanilla gradle";

    public static final String CACHES = "caches";
    public static final String LIBRARIES = "libraries";
    public static final String MAPPINGS = "mappings";
    public static final String REMAP = "remap";
    public static final String JOINED = "joined";
    /**
     * Group IDs of dependencies that should not be added to a server-only environment
     */
    public static final Set<String> CLIENT_ONLY_DEPENDENCY_GROUPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "oshi-project",
            "net.java.dev.jna",
            "com.ibm.icu",
            "net.java.jinput",
            "net.java.jutils",
            "org.lwjgl"
    )));

    private Constants() {
    }

    private static String version() {
        final String rawVersion = Constants.class.getPackage().getImplementationVersion();
        if (rawVersion == null) {
            return "dev";
        } else {
            return rawVersion;
        }
    }
}
