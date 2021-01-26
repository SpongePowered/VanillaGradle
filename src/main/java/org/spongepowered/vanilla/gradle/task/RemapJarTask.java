package org.spongepowered.vanilla.gradle.task;

import org.cadixdev.atlas.Atlas;
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.commons.ClassRemapper;
import org.spongepowered.vanilla.gradle.asm.LocalVariableNamingClassVisitor;
import org.spongepowered.vanilla.gradle.transformer.ExcludingJarEntryTransformer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.inject.Inject;

public class RemapJarTask extends DefaultTask {

    private final RegularFileProperty inputJar;
    private final RegularFileProperty mappingsFile;
    private final RegularFileProperty outputJar;

    @Inject
    public RemapJarTask(final ObjectFactory factory) {
        this.inputJar = factory.fileProperty();
        this.mappingsFile = factory.fileProperty();
        this.outputJar = factory.fileProperty();
    }

    @InputFile
    public RegularFileProperty getInputJar() {
        return this.inputJar;
    }

    @InputFile
    public RegularFileProperty getMappingsFile() {
        return this.mappingsFile;
    }

    @OutputFile
    public RegularFileProperty getOutputJar() {
        return this.outputJar;
    }

    @TaskAction
    public void execute() {
        final Atlas atlas = new Atlas();

        final MappingSet mappings;
        try {
            mappings = this.loadPgMappings(this.mappingsFile.get().getAsFile()).reverse();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        atlas.install(ctx -> {
            final JarEntryRemappingTransformer remapper = new JarEntryRemappingTransformer(new LorenzRemapper(mappings, ctx.inheritanceProvider()), (parent, mapper) -> {
                return new ClassRemapper(new LocalVariableNamingClassVisitor(parent), mapper); // strip snowpeople
            });
            // exclude some big libs to save time
            return ExcludingJarEntryTransformer.make(remapper, exclusions -> exclusions
                    .exclude("it.unimi")
                    .exclude("io.netty")
                    .exclude("com.google"));
        });

        try {
            atlas.run(this.getInputJar().get().getAsFile().toPath(), this.getOutputJar().get().getAsFile().toPath());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MappingSet loadPgMappings(final File source) throws IOException {
        final MappingSet set = MappingSet.create();
        try (final BufferedReader reader = Files.newBufferedReader(source.toPath(), StandardCharsets.UTF_8)) {
            final ProGuardReader proguard = new ProGuardReader(reader);
            proguard.read(set);
        }
        return set;
    }
}
