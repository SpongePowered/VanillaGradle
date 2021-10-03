package org.spongepowered.gradle.vanilla.repository.mappings;

import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.internal.repository.modifier.ArtifactModifier;
import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;

import java.io.IOException;
import java.util.Set;

public class DelegatingMappingsEntry extends MappingsEntry {
    private @Nullable NamedDomainObjectProvider<MappingsEntry> delegateTo;

    public DelegatingMappingsEntry(Project project, MinecraftExtension extension, String name) {
        super(project, extension, name);
    }

    public @Nullable NamedDomainObjectProvider<MappingsEntry> delegateTo() {
        return delegateTo;
    }
    public void delegateTo(MappingsEntry delegateTo) {
        delegateTo(extension.getMappings().named(delegateTo.getName()));
    }
    public void delegateTo(NamedDomainObjectProvider<MappingsEntry> delegateTo) {
        this.delegateTo = delegateTo;
    }
    public void delegateTo(String delegateTo) {
        delegateTo(extension.getMappings().named(delegateTo));
    }

    @Override
    protected <T extends MappingsEntry> MappingSet doResolve(
            MinecraftResolver.Context context,
            MinecraftResolver.MinecraftEnvironment environment,
            MinecraftPlatform platform,
            ArtifactModifier.SharedArtifactSupplier sharedArtifactSupplier,
            Set<String> alreadySeen) throws IOException {
        if (delegateTo == null) {
            throw new IllegalStateException("\"" + getName() + "\" delegateTo has not been initialized");
        }
        return extension.getMappings().getByName(delegateTo.getName()).resolve(context, environment, platform, sharedArtifactSupplier, alreadySeen);
    }
}
