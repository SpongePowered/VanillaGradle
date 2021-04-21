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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

public class MinecraftRepositoryExtensionImpl implements MinecraftRepositoryExtension {

    private final Property<Boolean> injectRepositories;
    final Property<MinecraftProviderService> providerService;
    final DirectoryProperty baseDir;

    @Inject
    public MinecraftRepositoryExtensionImpl(final ObjectFactory objects) {
        this.injectRepositories = objects.property(Boolean.class).convention(true);
        this.providerService = objects.property(MinecraftProviderService.class);
        this.baseDir = objects.directoryProperty();
    }

    @Override
    public Property<Boolean> injectRepositories() {
        return this.injectRepositories;
    }

    @Override
    public void injectRepositories(final boolean injectRepositories) {
        this.injectRepositories.set(injectRepositories);
    }

    @Override
    public String injectVersion(final String version) {
        try {
            return this.providerService.get().versions().inject(this.baseDir.file(version).get().getAsFile());
        } catch (final IOException ex) {
            throw new InvalidUserDataException("Unable to read version manifest from '" + version + "':", ex);
        }
    }

    @Override
    public String injectVersion(final File version) {
        try {
            return this.providerService.get().versions().inject(version);
        } catch (final IOException ex) {
            throw new InvalidUserDataException("Unable to read version manifest from '" + version + "':", ex);
        }
    }

}
