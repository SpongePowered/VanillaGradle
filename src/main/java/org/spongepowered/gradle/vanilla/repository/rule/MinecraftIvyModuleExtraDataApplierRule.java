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
package org.spongepowered.gradle.vanilla.repository.rule;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.model.VersionClassifier;
import org.spongepowered.gradle.vanilla.repository.IvyModuleWriter;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;

/**
 * Populate extra metadata based on what's written in our generated Ivy module files.
 *
 * <p>This would be easier if we could directly pass the version manifest in,
 * but Gradle seems to silently fail when this rule is provided the
 * resolver service.</p>
 */
public class MinecraftIvyModuleExtraDataApplierRule implements ComponentMetadataRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftIvyModuleExtraDataApplierRule.class);

    @Override
    public void execute(final ComponentMetadataContext context) {
        final @Nullable IvyModuleDescriptor ivy = context.getDescriptor(IvyModuleDescriptor.class);
        final ComponentMetadataDetails details = context.getDetails();
        final ModuleVersionIdentifier id = details.getId();
        if (!MinecraftPlatform.GROUP.equals(id.getGroup())) {
            throw new IllegalArgumentException(MinecraftIvyModuleExtraDataApplierRule.class + " was asked to process non-Minecraft dependency " + id);
        }
        final @Nullable MinecraftPlatform requestedPlatform = MinecraftPlatform.byId(id.getName()).orElse(null);
        if (requestedPlatform == null) { // unknown platform, let Gradle handle the resolution failure
            return;
        }

        details.setChanging(true); // Mojang can and will re-release versions >.>

        if (ivy == null) {
            return; // no extra data
        }

        // Set status scheme for MC version types
        details.setStatusScheme(VersionClassifier.ids());
        final @Nullable String mojangStatus = ivy.getExtraInfo().get(IvyModuleWriter.PROPERTY_MOJANG_STATUS);
        if (mojangStatus != null) {
            details.setStatus(mojangStatus);
        }

        final @Nullable String rawVersion = ivy.getExtraInfo().get(IvyModuleWriter.PROPERTY_JAVA_VERSION);
        try {
            if (rawVersion != null) {
                final int javaVersion = Integer.parseInt(rawVersion);
                details.allVariants(variant -> {
                    variant.attributes(attributes -> {
                        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion);
                    });
                });
            }
        } catch (final NumberFormatException ex) {
            MinecraftIvyModuleExtraDataApplierRule.LOGGER
                .warn("Invalid Java version found in metadata for module {}: '{}'", details.getId(), rawVersion);
        }
    }

}
