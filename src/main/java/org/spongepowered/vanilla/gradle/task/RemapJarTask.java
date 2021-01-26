package org.spongepowered.vanilla.gradle.task;

import org.cadixdev.atlas.Atlas;
import org.cadixdev.bombe.asm.jar.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.commons.ClassRemapper;
import org.spongepowered.vanilla.gradle.asm.LocalVariableNamingClassVisitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class RemapJarTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @InputFile
    public abstract RegularFileProperty getMappingsFile();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void execute() throws IOException {
        final Atlas atlas = new Atlas();

        MappingSet scratchMappings = MappingSet.create();
        try (final BufferedReader reader = Files.newBufferedReader(this.getMappingsFile().getAsFile().get().toPath(), StandardCharsets.UTF_8)) {
            final ProGuardReader proguard = new ProGuardReader(reader);
            proguard.read(scratchMappings);
        }

        final MappingSet mappings = scratchMappings.reverse();

        atlas.install(ctx -> new JarEntryRemappingTransformer(new LorenzRemapper(mappings, ctx.inheritanceProvider()), (parent, mapper) ->
            new ClassRemapper(new LocalVariableNamingClassVisitor(parent), mapper)));

        atlas.run(this.getInputJar().get().getAsFile().toPath(), this.getOutputJar().get().getAsFile().toPath());
    }
}
