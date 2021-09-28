package org.spongepowered.gradle.vanilla.internal.mappings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.model.ExtensionKey;
import org.cadixdev.lorenz.model.MethodMapping;
import org.parchmentmc.feather.io.gson.MDCGsonAdapterFactory;
import org.parchmentmc.feather.io.gson.OffsetDateTimeAdapter;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.io.gson.metadata.MetadataAdapterFactory;
import org.parchmentmc.feather.mapping.MappingDataContainer;
import org.parchmentmc.feather.util.SimpleVersion;

import java.io.BufferedReader;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read's the ParchmentMC's mapping format.
 */
public class ParchmentReader extends MappingsReader {
    public static final ExtensionKey<List<String>> JAVADOC_EXTENSION = new ExtensionKey<>((Class<List<String>>) (Object) List.class, "javadoc");
    public static final Gson GSON = new GsonBuilder()
            // Required for `MappingDataContainer` and inner data classes
            .registerTypeAdapterFactory(new MDCGsonAdapterFactory())
            // Required for `MappingDataContainer`s and `SourceMetadata`
            .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
            // Required for the metadata classes (`SourceMetadata`, `MethodReference`, etc.) and `Named`
            .registerTypeAdapterFactory(new MetadataAdapterFactory())
            // Required for parsing manifests: `LauncherManifest`, `VersionManifest`, and their inner data classes
            .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
            .create();

    private final BufferedReader parchment;

    public ParchmentReader(BufferedReader parchment) {
        this.parchment = parchment;
    }

    @Override
    public MappingSet read(MappingSet mappings) {
        MappingDataContainer parchmentData = GSON.fromJson(parchment, MappingDataContainer.class);
        for (MappingDataContainer.ClassData parchmentClassMapping : parchmentData.getClasses()) {
            // Remember that mojmap is named -> obf before its switched.
            mappings.getClassMapping(parchmentClassMapping.getName()).ifPresent(classMapping -> {
                classMapping.set(JAVADOC_EXTENSION, parchmentClassMapping.getJavadoc());

                classMapping.getFieldsByName().forEach((name, fieldMapping) -> {
                    MappingDataContainer.FieldData field = parchmentClassMapping.getField(name);
                    if (field != null && field.getDescriptor().equals(fieldMapping.getSignature().getType().get().toString())) {
                        fieldMapping.set(JAVADOC_EXTENSION, field.getJavadoc());
                    }
                });

                for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
                    MappingDataContainer.MethodData parchmentMethodMapping = parchmentClassMapping.getMethod(methodMapping.getObfuscatedName(), methodMapping.getObfuscatedDescriptor());
                    if (parchmentMethodMapping != null) {
                        methodMapping.set(JAVADOC_EXTENSION, parchmentMethodMapping.getJavadoc());
                    }
                }
            });
        }
        return mappings;
    }

    /**
     * The user closes their own {@link BufferedReader}. Nothing for us to do here.
     */
    @Override
    public void close() {
    }
}
