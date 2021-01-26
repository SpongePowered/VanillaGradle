package org.spongepowered.vanilla.gradle.transformer;

import org.cadixdev.bombe.jar.JarClassEntry;
import org.cadixdev.bombe.jar.JarEntryTransformer;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class ExcludingJarEntryTransformer implements JarEntryTransformer {

    private final JarEntryTransformer parent;
    private final Set<String> excludedPrefixes;

    public static Builder builder(final JarEntryTransformer parent) {
        return new Builder(Objects.requireNonNull(parent, "parent"));
    }

    public static ExcludingJarEntryTransformer make(final JarEntryTransformer parent, final Consumer<Builder> actor) {
        final Builder builder = new Builder(Objects.requireNonNull(parent, "parent"));
        actor.accept(builder);
        return builder.build();
    }

    private ExcludingJarEntryTransformer(final JarEntryTransformer parent, final Set<String> excludedPrefixes) {
        this.parent = parent;
        this.excludedPrefixes = new HashSet<>(excludedPrefixes);
    }

    @Override
    public JarClassEntry transform(final JarClassEntry entry) {
        for (final String prefix : this.excludedPrefixes) {
            if (entry.getName().startsWith(prefix)) {
                return entry;
            }
        }
        return this.parent.transform(entry);
    }

    public static class Builder {
        private final JarEntryTransformer parent;
        private final Set<String> excludedPrefixes = new HashSet<>();

        Builder(final JarEntryTransformer parent) {
            this.parent = parent;
        }

        public Builder exclude(final String prefix) {
            this.excludedPrefixes.add(prefix);
            return this;
        }

        public ExcludingJarEntryTransformer build() {
            return new ExcludingJarEntryTransformer(this.parent, this.excludedPrefixes);
        }
    }
}