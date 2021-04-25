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
package org.spongepowered.gradle.vanilla.repository;

import org.gradle.api.provider.Property;

import java.io.File;

public interface MinecraftRepositoryExtension {

    /**
     * Get whether repositories should be injected into the build.
     *
     * <p>For users who run their own proxying repositories or who want to
     * manage repositories in another way, this property can be disabled.
     * However, to function VanillaGradle must be able to resolve artifacts
     * that are present in the following repositories:</p>
     * <ul>
     *     <li>Maven Central</li>
     *     <li><a href="https://libraries.minecraft.net/">https://libraries.minecraft.net</a></li>
     *     <li><a href="https://files.minecraftforge.net/maven/">https://files.minecraftforge.net/maven/</a></li>
     * </ul>
     *
     * <p>This list is subject to change in any feature release without that
     * release being marked as "breaking".</p>
     *
     * @return the inject repositories property
     */
    Property<Boolean> injectRepositories();

    /**
     * Set whether standard repositories should be added to the project.
     *
     * @param injectRepositories whether repositories should be injected
     * @see #injectRepositories() for a list of repositories that are injected
     */
    void injectRepositories(boolean injectRepositories);

    /**
     * Inject a version from the provided file
     *
     * <p>This allows building against Minecraft versions that may not be
     * available in the ordinary version list, such as combat snapshots.</p>
     *
     * <p>This will override whatever data may be present in the manifest repository</p>
     *
     * @param file a path relative to the root directory
     */
    String injectVersion(final String file);

    /**
     * Inject a version from the provided file
     *
     * <p>This allows building against Minecraft versions that may not be
     * available in the ordinary version list, such as combat snapshots.</p>
     *
     * <p>This will override whatever data may be present in the manifest repository</p>
     *
     * @param file a file to load
     */
    String injectVersion(final File file);
}
