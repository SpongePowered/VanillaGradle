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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.artifacts.ComponentMetadataBuilder;
import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.model.VersionClassifier;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.model.VersionManifestRepository;

import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

public class LauncherMetaMetadataSupplier implements ComponentMetadataSupplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(LauncherMetaMetadataSupplier.class);
    private final Provider<MinecraftProviderService> versions;

    @Inject
    public LauncherMetaMetadataSupplier(final Provider<MinecraftProviderService> versions) {
        this.versions = versions;
    }

    @Override
    public void execute(final ComponentMetadataSupplierDetails details) {
        final ModuleComponentIdentifier id = details.getId();
        final ComponentMetadataBuilder result = details.getResult();
        LauncherMetaMetadataSupplier.LOGGER.warn("Preparing metadata for {}", id.getVersion());

        // This is designed for a fast lookup to be able to resolve dynamic versions
        final VersionDescriptor.@Nullable Reference descriptor;
        try {
            descriptor = this.versions.get().versions().manifest().get().findDescriptor(id.getVersion()).orElse(null);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        } catch (final ExecutionException ex) {
            LauncherMetaMetadataSupplier.LOGGER.warn("Failed to resolve version manifest while populating component metadata: ", ex);
            return;
        }

        // Status
        result.setStatusScheme(VersionClassifier.ids());
        if (descriptor == null) {
            return;
        }

        result.setStatus(descriptor.type().id());
    }
}
