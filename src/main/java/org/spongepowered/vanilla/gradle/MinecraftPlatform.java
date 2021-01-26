package org.spongepowered.vanilla.gradle;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum MinecraftPlatform {
    CLIENT(MinecraftSide.CLIENT),
    SERVER(MinecraftSide.SERVER),
    JOINED(MinecraftSide.CLIENT, MinecraftSide.SERVER);

    private final Set<MinecraftSide> activeSides;

    MinecraftPlatform(final MinecraftSide... sides) {
        this.activeSides = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(sides)));
    }

    public boolean includes(final MinecraftSide platform) {
        return this.activeSides.contains(platform);
    }

    public Set<MinecraftSide> activeSides() {
        return this.activeSides;
    }
}
