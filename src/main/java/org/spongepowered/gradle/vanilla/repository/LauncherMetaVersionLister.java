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

import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataVersionLister;
import org.gradle.api.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.model.VersionManifestRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Provide available Minecraft versions based on the configured manifest.
 */
public class LauncherMetaVersionLister implements ComponentMetadataVersionLister {
    private static final Logger LOGGER = LoggerFactory.getLogger(LauncherMetaVersionLister.class);
    private final Provider<MinecraftProviderService> manifests;

    public LauncherMetaVersionLister(final Provider<MinecraftProviderService> manifests) {
        this.manifests = manifests;
    }

    @Override
    public void execute(final ComponentMetadataListerDetails version) {
        if (!MinecraftPlatform.GROUP.equals(version.getModuleIdentifier().getGroup())) {
            return;
        }
        LauncherMetaVersionLister.LOGGER.info("Listing versions for {}", version.getModuleIdentifier());

        try {
            final List<? extends VersionDescriptor> versions = this.manifests.get().versions().availableVersions().get();
            final List<String> availableVersions = new ArrayList<>(versions.size());
            for (final VersionDescriptor descriptor : versions) {
                availableVersions.add(descriptor.id());
            }
            version.listed(availableVersions);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException ex) {
            LauncherMetaVersionLister.LOGGER.warn("Failed to determine available Minecraft versions:", ex.getCause());
        }
    }
}
