package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.ExtensionKey;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.MethodParameterMapping;
import org.parchmentmc.feather.io.gson.MDCGsonAdapterFactory;
import org.parchmentmc.feather.io.gson.OffsetDateTimeAdapter;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.io.gson.metadata.MetadataAdapterFactory;
import org.parchmentmc.feather.mapping.MappingDataContainer;
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer;
import org.parchmentmc.feather.util.SimpleVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public class ParchmentReaderImpl {
    @SuppressWarnings("unchecked")
    private static final ExtensionKey<List<String>> JAVADOC_EXTENSION = new ExtensionKey<>((Class<List<String>>) (Class<?>) List.class, "javadoc");
    private static final Gson GSON = new GsonBuilder()
            // Required for `MappingDataContainer` and inner data classes
            .registerTypeAdapterFactory(new MDCGsonAdapterFactory())
            // Required for `MappingDataContainer`s and `SourceMetadata`
            .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
            // Required for the metadata classes (`SourceMetadata`, `MethodReference`, etc.) and `Named`
            .registerTypeAdapterFactory(new MetadataAdapterFactory())
            // Required for parsing manifests: `LauncherManifest`, `VersionManifest`, and their inner data classes
            .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
            .create();

    public static void read(MappingSet output, Path file) throws IOException {
        final MappingDataContainer parchmentData;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            parchmentData = GSON.fromJson(reader, VersionedMappingDataContainer.class);
        }
        for (final MappingDataContainer.ClassData parchmentClassMapping : parchmentData.getClasses()) {
            final ClassMapping<?, ?> classMapping = output.getOrCreateClassMapping(parchmentClassMapping.getName());
            classMapping.set(JAVADOC_EXTENSION, parchmentClassMapping.getJavadoc());

            for (final MappingDataContainer.FieldData field : parchmentClassMapping.getFields()) {
                final FieldMapping fieldMapping = classMapping.getOrCreateFieldMapping(field.getName(), field.getDescriptor());
                fieldMapping.set(JAVADOC_EXTENSION, field.getJavadoc());
            }

            for (final MappingDataContainer.MethodData method : parchmentClassMapping.getMethods()) {
                final MethodMapping methodMapping = classMapping.getOrCreateMethodMapping(method.getName(), method.getDescriptor());
                methodMapping.set(JAVADOC_EXTENSION, method.getJavadoc());
                for (final MappingDataContainer.ParameterData parameter : method.getParameters()) {
                    final MethodParameterMapping parameterMapping = methodMapping.getOrCreateParameterMapping(parameter.getIndex());
                    if (parameter.getName() != null) {
                        parameterMapping.setDeobfuscatedName(parameter.getName());
                    }
                    if (parameter.getJavadoc() != null) {
                        parameterMapping.set(JAVADOC_EXTENSION, Collections.singletonList(parameter.getJavadoc()));
                    }
                }
            }
        }
    }
}
