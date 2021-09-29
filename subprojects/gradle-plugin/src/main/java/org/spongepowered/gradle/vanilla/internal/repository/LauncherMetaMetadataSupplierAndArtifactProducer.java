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
package org.spongepowered.gradle.vanilla.internal.repository;

import org.gradle.api.artifacts.ComponentMetadataBuilder;
import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.internal.model.VersionClassifier;
import org.spongepowered.gradle.vanilla.internal.model.VersionDescriptor;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.resolver.ResolutionResult;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

public class LauncherMetaMetadataSupplierAndArtifactProducer implements ComponentMetadataSupplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(LauncherMetaMetadataSupplierAndArtifactProducer.class);
    private final Provider<MinecraftProviderService> providerService;

    @Inject
    public LauncherMetaMetadataSupplierAndArtifactProducer(final Provider<MinecraftProviderService> providerService) {
        this.providerService = providerService;
    }

    @Override
    public void execute(final ComponentMetadataSupplierDetails details) {
        final ModuleComponentIdentifier id = details.getId();
        final ComponentMetadataBuilder result = details.getResult();
        LauncherMetaMetadataSupplierAndArtifactProducer.LOGGER.info("Preparing metadata for {}", id.getVersion());
        final String[] components = id.getModule().split("_", 2); // any manually specified components will exist, but can't be resolved
        if (components.length == 0) {
            return; // failed
        }
        final Optional<MinecraftPlatform> platform = MinecraftPlatform.byId(components[0]);
        if (!platform.isPresent()) {
            return;
        }

        final MinecraftProviderService providerService = this.providerService.get();

        final String version = id.getVersion();
        final VersionDescriptor.Full descriptor;
        LauncherMetaMetadataSupplierAndArtifactProducer.LOGGER.info("Attempting to resolve minecraft {} version {}", id.getModule(), version);
        try {
            final MinecraftResolver resolver = providerService.resolver();
            // Request the appropriate jar, block until it's provided
            // TODO: maybe validate that the state keys of the provided modifiers actually match the artifact ID?
            final ResolutionResult<MinecraftResolver.MinecraftEnvironment>
                resolution = resolver.provide(platform.get(), version, providerService.peekModifiers(), providerService.peekMappings()).get();
            if (!resolution.isPresent()) {
                return;
            }

            descriptor = resolution.get().metadata();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        } catch (final ExecutionException ex) {
            // log exception but don't throw, dependency resolution failure will come anyways when Gradle provides a message
            LauncherMetaMetadataSupplierAndArtifactProducer.LOGGER.error("Failed to resolve Minecraft {} version {}:", platform.get(), version, ex.getCause());
            return;
        }

        // Status
        result.setStatusScheme(VersionClassifier.ids());
        result.setStatus(descriptor.type().id());
    }
}
