package org.spongepowered.vanilla.gradle;

public final class Constants {

    public static final String NAME = "VanillaGradle";
    public static final String VERSION = Constants.version();
    public static final String API_V2_ENDPOINT = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    public static final String CACHE = "caches";
    public static final String LIBRARIES = "libraries";

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
