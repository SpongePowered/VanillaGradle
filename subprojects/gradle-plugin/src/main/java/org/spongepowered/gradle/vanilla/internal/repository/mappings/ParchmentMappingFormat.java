package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.gradle.vanilla.internal.repository.ResolvableTool;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingFormat;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ParchmentMappingFormat extends MappingFormat<@NonNull MappingsEntry> {
    public ParchmentMappingFormat() {
        super(MappingsEntry.class);
    }

    @Override
    public @NonNull String getName() {
        return "parchment";
    }

    @Override
    public @NonNull MappingSet read(
            @NonNull Path file,
            @NonNull MappingsEntry entry,
            MinecraftResolver.@NonNull Context context
    ) throws IOException {
        boolean isTempFile = false;
        if (file.toString().endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file.toFile())) {
                ZipEntry zipEntry = zip.getEntry("parchment.json");
                if (zipEntry != null) {
                    file = Files.createTempFile(file.getFileName().toString(), "mappings");
                    Files.copy(zip.getInputStream(zipEntry), file, StandardCopyOption.REPLACE_EXISTING);
                    isTempFile = true;
                }
            }
        }
        MappingSet mappings = MappingSet.create();
        URLClassLoader classLoader = CompletableFuture.supplyAsync(() -> context.classLoaderWithTool(ResolvableTool.REMAP_PARCHMENT).get(), context.syncExecutor()).join();
        try {
            Class<?> readerClass = Class.forName(
                    "org.spongepowered.gradle.vanilla.internal.repository.mappings.ParchmentReaderImpl",
                    true,
                    classLoader
            );
            Method readMethod = readerClass.getMethod("read", MappingSet.class, Path.class);
            readMethod.invoke(null, mappings, file);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        if (isTempFile) {
            Files.delete(file);
        }
        return mappings;
    }
}
