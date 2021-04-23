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
package org.spongepowered.gradle.vanilla.util;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * A classloader that will load classes from itself rather than its parent where possible.
 */
public class SelfPreferringClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final ClassLoader parent;

    public SelfPreferringClassLoader(final URL[] urls, final ClassLoader parent) {
        super(urls, parent);
        this.parent = parent;
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (this.getClassLoadingLock(name)) {
            Class<?> result = this.findLoadedClass(name);
            if (result == null) {
                try {
                    result = this.findClass(name);
                } catch (final ClassNotFoundException ex) {
                    // ignore, delegate to parent
                }
            }

            if (result == null) {
                return super.loadClass(name, resolve);
            }

            if (resolve) {
                this.resolveClass(result);
            }
            return result;
        }
    }

    @Override
    public @Nullable URL getResource(final String name) {
        @Nullable URL result = this.findResource(name);
        if (result == null) {
            result = super.getResource(name);
        }
        return result;
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        return new Enumeration<URL>() {

            @Nullable Enumeration<URL> active = SelfPreferringClassLoader.this.findResources(name);
            @Nullable Enumeration<URL> staged = SelfPreferringClassLoader.this.parent == null
                                                ? ClassLoader.getSystemClassLoader().getResources(name)
                                                : SelfPreferringClassLoader.this.parent.getResources(name);

            private @Nullable Enumeration<URL> nextComponent() {
                if (this.active == null) {
                    return null;
                } else if (!this.active.hasMoreElements()) {
                    this.active = this.staged;
                    this.staged = null;
                }
                return this.active;
            }

            @Override
            public boolean hasMoreElements() {
                final @Nullable Enumeration<URL> component = this.nextComponent();
                return component != null && component.hasMoreElements();
            }

            @Override
            public URL nextElement() {
                final @Nullable Enumeration<URL> component = this.nextComponent();
                if (component == null) {
                    throw new NoSuchElementException();
                }
                return component.nextElement();
            }

        };
    }

}
