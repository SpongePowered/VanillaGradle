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
package org.spongepowered.vanilla.gradle;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Constants {

    public static final String NAME = "VanillaGradle";
    public static final String VERSION = Constants.version();
    public static final String API_V2_ENDPOINT = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String LIBRARIES_MAVEN_URL = "https://libraries.minecraft.net";
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
