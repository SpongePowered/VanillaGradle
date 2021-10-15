package org.spongepowered.gradle.vanilla.repository.mappings;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.spongepowered.gradle.vanilla.MinecraftExtension;

public class TinyMappingsEntry extends MappingsEntry {
    private final Property<String> from;
    private final Property<String> to;

    public TinyMappingsEntry(
            Project project,
            MinecraftExtension extension,
            String name
    ) {
        super(project, extension, name);
        this.from = project.getObjects().property(String.class);
        this.to = project.getObjects().property(String.class);
    }

    public Property<String> from() {
        return from;
    }

    public void from(String from) {
        this.from.set(from);
    }

    public Property<String> to() {
        return to;
    }

    public void to(String to) {
        this.to.set(to);
    }
}
