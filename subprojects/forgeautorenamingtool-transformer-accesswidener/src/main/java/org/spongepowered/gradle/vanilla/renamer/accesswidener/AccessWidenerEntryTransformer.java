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
package org.spongepowered.gradle.vanilla.renamer.accesswidener;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.minecraftforge.fart.api.Transformer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.immutables.metainf.Metainf;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.gradle.vanilla.renamer.spi.TransformerProvider;
import org.spongepowered.gradle.vanilla.renamer.spi.TransformerProvisionException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@Metainf.Service
public final class AccessWidenerEntryTransformer implements TransformerProvider {

    private static final String ID = "access-widener";

    private @MonotonicNonNull OptionSpec<File> triggerOption;

    @Override
    public @NonNull String id() {
        return AccessWidenerEntryTransformer.ID;
    }

    @Override
    public OptionSpec<?> decorateTriggerOption(final @NonNull OptionSpecBuilder orig) {
        return this.triggerOption = orig.withRequiredArg().ofType(File.class);
    }

    @Override
    public Transformer.Factory create(final @NonNull OptionSet options) throws TransformerProvisionException {
        final List<File> accessWideners = options.valuesOf(this.triggerOption);
        final AccessWidener widener = new AccessWidener();
        final AccessWidenerReader reader = new AccessWidenerReader(widener);

        for (final File widenerFile : accessWideners) {
            try (final BufferedReader fileReader = Files.newBufferedReader(widenerFile.toPath(), StandardCharsets.UTF_8)) {
                reader.read(fileReader);
            } catch (final IOException ex) {
                throw new TransformerProvisionException("Failed to read access widener from file " + widenerFile, ex);
            }
        }

        return ctx -> new Action(widener);
    }

    static final class Action implements Transformer {

        private final AccessWidener widener;

        Action(final AccessWidener widener) {
            this.widener = widener;
        }

        @Override
        public ClassEntry process(final ClassEntry entry) {
            // Because InnerClass attributes can be present in any class AW'd classes
            // are referenced from, we have to target every class to get a correct output.
            final ClassReader reader = new ClassReader(entry.getData());
            final ClassWriter writer = new ClassWriter(reader, 0);
            // TODO: Expose the ASM version constant somewhere visible to this worker
            final ClassVisitor visitor = AccessWidenerVisitor.createClassVisitor(Opcodes.ASM9, writer, this.widener);
            reader.accept(visitor, 0);
            if (entry.isMultiRelease()) {
                return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray(), entry.getVersion());
            } else {
                return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
            }
        }

    }

}
