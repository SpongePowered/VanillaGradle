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
package org.spongepowered.gradle.vanilla.internal.repository.rule;

import org.gradle.api.artifacts.CapabilitiesResolution;
import org.gradle.api.artifacts.CapabilityResolutionDetails;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.ComponentVariantIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;

import java.util.List;

/**
 * Tell Gradle that the 'joined' artifact provides both a server and client.
 */
public class JoinedProvidesClientAndServerRule implements ComponentMetadataRule {
    private static final Logger LOGGER = LoggerFactory.getLogger(JoinedProvidesClientAndServerRule.class);

    @Override
    public void execute(final ComponentMetadataContext context) {
        context.getDetails().allVariants(variant -> {
            JoinedProvidesClientAndServerRule.LOGGER.info("Seeing variant {}", variant);
            try {
                variant.withCapabilities(capabilities -> {
                try {
                    final String version = context.getDetails().getId().getVersion();
                    capabilities.addCapability(MinecraftPlatform.GROUP, MinecraftPlatform.SERVER.artifactId(), version);
                    capabilities.addCapability(MinecraftPlatform.GROUP, MinecraftPlatform.CLIENT.artifactId(), version);
                } catch (final Throwable thr) {
                    JoinedProvidesClientAndServerRule.LOGGER.warn("Failed to set capabilities", thr);
                }
            });
            } catch (final Throwable thr) {
                JoinedProvidesClientAndServerRule.LOGGER.warn("Failed to withCapabilities", thr);
            }
        });
    }

    public static void configureResolution(final CapabilitiesResolution resolution) {
        // set up rules so that when both joined and sided Minecraft instances are present on the classpath, we should
        resolution.withCapability(
            MinecraftPlatform.GROUP,
            MinecraftPlatform.SERVER.artifactId(),
            JoinedProvidesClientAndServerRule::selectJoined
        );
        resolution.withCapability(
            MinecraftPlatform.GROUP,
            MinecraftPlatform.CLIENT.artifactId(),
            JoinedProvidesClientAndServerRule::selectJoined
        );
    }

    private static void selectJoined(final CapabilityResolutionDetails details) {
        final List<ComponentVariantIdentifier> candidates = details.getCandidates();
        for (int i = 0; i < candidates.size(); i++) {
            final ComponentVariantIdentifier id = candidates.get(i);
            // TODO: is this the right logic if the joined artifact is at a lower version than the single-sided artifact?
            if (id.getId() instanceof ModuleComponentIdentifier
             && ((ModuleComponentIdentifier) id.getId()).getModule().equals(MinecraftPlatform.JOINED.artifactId())) {
                details.select(id).because("Selecting joined artifact version "
                    + ((ModuleComponentIdentifier) id.getId()).getVersion() + " because it contains both client and server");
            }
        }
    }
}
