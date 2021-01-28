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
package org.spongepowered.gradle.vanilla.model.rule;

import com.google.gson.reflect.TypeToken;

import java.util.Objects;
import java.util.regex.Pattern;

public final class OperatingSystemRule implements Rule<OperatingSystemRule.OSInfo> {

    private static final String CTX_OS_NAME = "vanillagradle:os_name";
    private static final String CTX_OS_VERSION = "vanillagradle:os_version";

    public static final OperatingSystemRule INSTANCE = new OperatingSystemRule();
    private static final TypeToken<OSInfo> TYPE = TypeToken.get(OSInfo.class);

    public static final class OSInfo {
        final String name;
        final Pattern version;

        public OSInfo(final String name, final Pattern version) {
            this.name = name;
            this.version = version;
        }
    }

    public static void setOsName(final RuleContext ctx, final String name) {
        ctx.put(OperatingSystemRule.CTX_OS_NAME, name);
    }

    public static void setOsVersion(final RuleContext ctx, final String version) {
        ctx.put(OperatingSystemRule.CTX_OS_VERSION, version);
    }

    private OperatingSystemRule() {
    }

    @Override
    public String id() {
        return "os";
    }

    @Override
    public TypeToken<OSInfo> type() {
        return TYPE;
    }

    @Override
    public boolean test(final RuleContext context, final OSInfo value) {
        final String osName = context.<String>get(OperatingSystemRule.CTX_OS_NAME).orElseGet(() -> System.getProperty("os.name"));
        final String osVersion = context.<String>get(OperatingSystemRule.CTX_OS_VERSION).orElseGet(() -> System.getProperty("os.version"));

        return Objects.equals(osName, value.name)
                && (value.version == null || value.version.matcher(osVersion).find());
    }

}
