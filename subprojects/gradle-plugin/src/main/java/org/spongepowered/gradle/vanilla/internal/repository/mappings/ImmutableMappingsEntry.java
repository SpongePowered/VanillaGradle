package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.Project;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;

public abstract class ImmutableMappingsEntry extends MappingsEntry {
    private boolean hasInitialized = false;

    public ImmutableMappingsEntry(Project project, MinecraftExtension extension, String name, String format) {
        super(project, extension, name);
        format(format);
        hasInitialized = true;
    }

    @Override
    public void format(@NonNull String format) {
        if (hasInitialized) {
            throw new IllegalStateException("Cannot modify the format of \"" + getName() + "\"");
        } else {
            super.format(format);
        }
    }

    @Override
    public void dependency(@Nullable Object dependencyNotation) {
        throw new IllegalStateException("Cannot modify the dependency of \"" + getName() + "\"");
    }

    @Override
    public void parent(@Nullable String parent) {
        throw new IllegalStateException("Cannot modify the parent of \"" + getName() + "\"");
    }

    @Override
    public void inverse(boolean isInverse) {
        throw new IllegalStateException("Cannot invert \"" + getName() + "\"");
    }
}
