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
package org.spongepowered.gradle.vanilla.internal.util;

import groovy.lang.Closure;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;

public final class GradleCompat {

    private static final boolean HAS_ORG_GRADLE_UTIL_INTERNAL_CONFIGUREUTIL;
    static  {
        boolean hasNewConfigureUtil;
        try {
            Class.forName("org.gradle.util.internal.ConfigureUtil");
            hasNewConfigureUtil = true;
        } catch (final ClassNotFoundException ex) {
            hasNewConfigureUtil = false;
        }
        HAS_ORG_GRADLE_UTIL_INTERNAL_CONFIGUREUTIL = hasNewConfigureUtil;
    }

    private GradleCompat() {
    }

    @SuppressWarnings({"rawtypes", "deprecation"})
    public static <T extends NamedDomainObjectContainer<?>> T configureSelf(final T configureTarget, final Closure configureClosure) {
        // TODO: This uses internal API, see if there's a more 'public' way to do this
        if (HAS_ORG_GRADLE_UTIL_INTERNAL_CONFIGUREUTIL) {
            return org.gradle.util.internal.ConfigureUtil.configureSelf(configureClosure, configureTarget, new NamedDomainObjectContainerConfigureDelegate(configureClosure, configureTarget));
        } else {
            return org.gradle.util.ConfigureUtil.configureSelf(configureClosure, configureTarget, new NamedDomainObjectContainerConfigureDelegate(configureClosure, configureTarget));
        }
    }

}
