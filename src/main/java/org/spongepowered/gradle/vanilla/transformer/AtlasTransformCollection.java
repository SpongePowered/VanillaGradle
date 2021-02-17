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
package org.spongepowered.gradle.vanilla.transformer;

import org.cadixdev.atlas.Atlas;
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.objectweb.asm.commons.ClassRemapper;
import org.spongepowered.gradle.vanilla.asm.EnhancedClassRemapper;
import org.spongepowered.gradle.vanilla.asm.LocalVariableNamingClassVisitor;
import org.spongepowered.gradle.vanilla.asm.SyntheticParameterAnnotationsFix;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

import javax.inject.Inject;

public class AtlasTransformCollection {
    private final ListProperty<Action<? super Atlas>> transformations;
    private final ObjectFactory objects;

    @Inject
    @SuppressWarnings({"rawtypes", "unchecked"})
    public AtlasTransformCollection(final ObjectFactory objects) {
        this.objects = objects;
        this.transformations = (ListProperty) objects.listProperty(Action.class);
    }

    @Nested
    public ListProperty<Action<? super Atlas>> getTransformations() {
        return this.transformations;
    }

    /**
     * Create and add a new action by type.
     *
     * @param type the action type
     * @param <T> the value type
     * @return the created action, unconfigured
     */
    public <T extends Action<? super Atlas>> T add(final Class<T> type) {
        final T value = this.objects.newInstance(type);
        this.transformations.add(value);
        return value;
    }

    /**
     * Create, add, and configure a new action by type.
     *
     * @param type the action type
     * @param <T> the value type
     * @return the created action, unconfigured
     */
    public <T extends Action<? super Atlas>> T add(final Class<T> type, final Action<T> configureAction) {
        final T value = this.add(type);
        Objects.requireNonNull(configureAction, "configureAction").execute(value);
        return value;
    }

    /**
     * Create an action that will filter entries to classes matching the
     * specified criteria.
     *
     * @return a new, unconfigured action
     */
    public FilterEntriesAction filterEntries() {
        return this.add(FilterEntriesAction.class);
    }

    /**
     * Create an action that will filter entries to classes matching the
     * specified criteria.
     *
     * @return a new configured action
     */
    public FilterEntriesAction filterEntries(final Action<FilterEntriesAction> configure) {
        return this.add(FilterEntriesAction.class, configure);
    }

    public static abstract class FilterEntriesAction implements Action<Atlas> {

        @Input
        public abstract SetProperty<String> getAllowedPackages();

        @Override
        public void execute(final Atlas atlas) {
            atlas.install(ctx -> new FilterClassesTransformer(this.getAllowedPackages().get()));
        }
    }

    /**
     * Create and add an action that will strip signatures from a jar.
     *
     * <p>No configuration options are currently available</p>
     *
     * @return the action
     */
    public StripSignaturesAction stripSignatures() {
        return this.add(StripSignaturesAction.class);
    }

    public static abstract class StripSignaturesAction implements Action<Atlas> {

        @Override
        public void execute(final Atlas atlas) {
            atlas.install(ctx -> SignatureStripperTransformer.INSTANCE);
        }

    }

    /**
     * Create and add an action to remap a jar.
     *
     * <p>LVT normalization will be applied, as well as any other common
     * bytecode fixes.</p>
     *
     * @return a new, unconfigured remap action
     */
    public RemapAction remap() {
        return this.add(RemapAction.class);
    }

    /**
     * Create, add, and configure an action to remap a jar.
     *
     * <p>LVT normalization will be applied, as well as any other common
     * bytecode fixes.</p>
     *
     * @return a new configured remap action
     */
    public RemapAction remap(final Action<RemapAction> configure) {
        return this.add(RemapAction.class, configure);
    }

    public static abstract class RemapAction implements Action<Atlas> {

        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        public abstract RegularFileProperty getMappingsFile();

        @Override
        public void execute(final Atlas atlas) {
            final MappingSet scratchMappings = MappingSet.create();
            try (final BufferedReader reader = Files.newBufferedReader(this.getMappingsFile().get().getAsFile().toPath(), StandardCharsets.UTF_8)) {
                final ProGuardReader proguard = new ProGuardReader(reader);
                proguard.read(scratchMappings);
            } catch (final IOException ex) {
                throw new GradleException("Failed to read mappings from " + this.getMappingsFile().get().getAsFile(), ex);
            }
            final MappingSet mappings = scratchMappings.reverse();

            atlas.install(ctx -> new JarEntryRemappingTransformer(new LorenzRemapper(mappings, ctx.inheritanceProvider()), (parent, mapper) ->
                new EnhancedClassRemapper(new SyntheticParameterAnnotationsFix(new LocalVariableNamingClassVisitor(parent)), mapper)));

        }
    }

}
