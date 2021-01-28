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
package org.spongepowered.gradle.vanilla.runs;

/**
 * Tokens that can be replaced in Mojang launcher manifest argument sections.
 *
 * <p>These tokens are created from those known at time of writing, and are
 * subject to change at any time.</p>
 */
public final class ClientRunParameterTokens {

    public static final String AUTH_PLAYER_NAME = "auth_player_name";
    public static final String VERSION_NAME = "version_name";
    public static final String GAME_DIRECTORY = "game_directory";
    public static final String ASSETS_ROOT = "assets_root";
    public static final String ASSETS_INDEX_NAME = "assets_index_name";
    public static final String AUTH_UUID = "auth_uuid";
    public static final String AUTH_ACCESS_TOKEN = "auth_access_token";
    public static final String USER_TYPE = "user_type";
    public static final String VERSION_TYPE = "version_type";
    public static final String RESOLUTION_WIDTH = "resolution_width";
    public static final String RESOLUTION_HEIGHT = "resolution_height";
    public static final String NATIVES_DIRECTORY = "natives_directory";
    public static final String LAUNCHER_NAME = "launcher_name";
    public static final String LAUNCHER_VERSION = "launcher_version";
    public static final String CLASSPATH = "classpath";

    private ClientRunParameterTokens() {
    }
}
