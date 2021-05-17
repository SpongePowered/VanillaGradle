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
/**
 * A service that handles artifact resolution and caching.
 *
 * <p>The basic types of artifact are:</p>
 * <ul>
 *     <li>Flat URL file</li>
 *     <li>Maven-style (has metadata attached)</li>
 *     <li>Minecraft version (has metadata attached in the form of its {@link org.spongepowered.gradle.vanilla.internal.model.VersionDescriptor.Full})</li>
 *     <li>Deserialized JSON object via a URL (or file) endpoint</li>
 * </ul>
 *
 * <h2>Caching</h2>
 * <p>Caches can resolve any sort of artifact. They support a parent cache.
 * To be safe for use across multiple processes, caches will be write-locked
 * while a single process is performing its writes.</p>
 *
 * <h2>Resolution</h2>
 * <p>Artifacts are resolved in several stages. These are:</p>
 * <ol>
 *     <li>(optional) Resolve metadata. Metadata is an artifact type like any other.</li>
 *     <li>(optional) Resolve dependencies</li>
 * </ol>
 */
@DefaultQualifier(NonNull.class)
package org.spongepowered.gradle.vanilla.internal.network.network;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
