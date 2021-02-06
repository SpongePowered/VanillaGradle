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
package org.spongepowered.gradle.vanilla;

import org.objectweb.asm.Opcodes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Constants {

    public static final String NAME = "VanillaGradle";
    public static final String VERSION = Constants.version();
    public static final String MINECRAFT_RESOURCES_URL = "https://resources.download.minecraft.net/";
    public static final String TASK_GROUP = "vanilla gradle";
    public static final int ASM_VERSION = Opcodes.ASM9;
    public static final String FIRST_TARGETABLE_RELEASE_TIMESTAMP = "2019-09-04T11:19:34+00:00"; // 19w36a+
    public static final String OUT_OF_BAND_RELEASE = "1.14.4"; // Cause it is special

    public static final class Directories {
        public static final String CACHES = "caches";
        public static final String ASSETS = "assets";
        public static final String JARS = "jars";
        public static final String REMAPPED = "remapped";
        public static final String DECOMPILED = "decompiled";
        public static final String ORIGINAL = "original";
        public static final String MAPPINGS = "mappings";
        public static final String FILTERED = "filtered";
        public static final String MANIFESTS = "manifests";

        private Directories() {
        }
    }

    public static final class Manifests {
        public static final String SKIP_CACHE = "vanillagradle.skipManifestCache";
        public static final String API_V2_ENDPOINT = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
        public static final long CACHE_TIMEOUT_SECONDS = 24 /* hours */ * 60 /* minutes/hr */ * 60 /* seconds/min */;

        private Manifests() {
        }
    }

    public static final class Tasks {

        public static final String ACCESS_WIDENER = "accessWidenMinecraft";
        public static final String DOWNLOAD_ASSETS = "downloadAssets";
        public static final String COLLECT_NATIVES = "collectNatives";
        public static final String PREPARE_WORKSPACE = "prepareWorkspace";
        public static final String DECOMPILE = "decompile";

        private Tasks() {
        }
    }

    public static final class RunConfiguration {
        public static final String SERVER_CONFIG = "server";
        public static final String CLIENT_CONFIG = "client";

        private RunConfiguration() {
        }
    }

    public static final class WorkerDependencies {
        public static final String MERGE_TOOL = "net.minecraftforge:mergetool:1.1.1";
        public static final String ACCESS_WIDENER = "net.fabricmc:access-widener:1.0.2";
        public static final String FORGE_FLOWER = "net.minecraftforge:forgeflower:1.5.478.18";

        private WorkerDependencies() {
        }
    }

    public static final class Repositories {
        public static final String MINECRAFT = "https://libraries.minecraft.net/";
        public static final String MINECRAFT_FORGE = "https://files.minecraftforge.net/maven/";

        private Repositories() {
        }
    }

    public static final class Configurations {
        public static final String MINECRAFT = "minecraft";
        public static final String MINECRAFT_CLASSPATH = "minecraftClasspath";
        public static final String MERGETOOL = "mergetool";
        public static final String ACCESS_WIDENER = "accessWidener";
        public static final String FORGE_FLOWER = "forgeFlower";

        private Configurations() {
        }
    }

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
