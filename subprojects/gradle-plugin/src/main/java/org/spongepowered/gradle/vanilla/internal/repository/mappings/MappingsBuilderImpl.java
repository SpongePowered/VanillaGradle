package org.spongepowered.gradle.vanilla.internal.repository.mappings;

import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingMethodException;
import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.spongepowered.gradle.vanilla.repository.MappingsBuilder;
import org.spongepowered.gradle.vanilla.repository.MappingsReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MappingsBuilderImpl extends GroovyObjectSupport implements MappingsBuilder {
    private final Project project;
    private final List<Layer> layers = new ArrayList<>();

    public MappingsBuilderImpl(final Project project) {
        this.project = project;
    }

    @Override
    public void add(@NonNull final String formatName, @NonNull final Object dependencyNotation) {
        final String configName = "mappingsLayer" + layers.size();
        project.getConfigurations().register(configName, config -> {
            config.setVisible(false);
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
        });
        Dependency dependency = project.getDependencies().add(configName, dependencyNotation);
        layers.add(new Layer(formatName, configName, dependency));
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public @Nullable MappingSet create(List<MappingsReader> readers) {
        return layers.stream().map(layer -> layer.resolve(project, readers)).reduce(MappingSet::merge).orElse(null);
    }

    public void computeStateKey(MessageDigest digest) {
        for (Layer layer : layers) {
            layer.computeStateKey(digest);
        }
    }

    @Override
    public Object invokeMethod(String name, Object arg) {
        try {
            return super.invokeMethod(name, arg);
        } catch (MissingMethodException e) {
            final Object[] args = (Object[]) arg;
            if (args.length == 1) {
                add(name, args[0]);
                return null;
            } else {
                throw e;
            }
        }
    }

    private static class Layer {
        private final String format;
        private final String config;
        private final Dependency dependency;

        Layer(String format, String config, Dependency dependency) {
            this.format = format;
            this.config = config;
            this.dependency = dependency;
        }

        MappingSet resolve(final Project project, final List<MappingsReader> readers) {
            Set<File> files = project.getConfigurations().getByName(config).resolve();
            if (files.size() != 1) {
                throw new IllegalStateException("Mappings configuration didn't resolve to exactly one file");
            }
            for (MappingsReader reader : readers) {
                if (reader.getName().equals(format)) {
                    try {
                        return reader.read(files.iterator().next().toPath());
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read mappings file", e);
                    }
                }
            }
            throw new IllegalStateException("Could not find a mappings reader for format \"" + format + "\". Maybe there is a Gradle plugin missing.");
        }

        void computeStateKey(MessageDigest digest) {
            // we can't resolve the dependency at this point, try some heuristics

            if (dependency instanceof ModuleDependency) {
                for (DependencyArtifact artifact : ((ModuleDependency) dependency).getArtifacts()) {
                    digest.update(artifact.getUrl().getBytes(StandardCharsets.UTF_8));
                }
            } else if (dependency instanceof FileCollectionDependency) {
                for (File file : ((FileCollectionDependency) dependency).getFiles()) {
                    try (final InputStream is = new FileInputStream(file)) {
                        final byte[] buf = new byte[4096];
                        int read;
                        while ((read = is.read(buf)) != -1) {
                            digest.update(buf, 0, read);
                        }
                    } catch (final IOException ex) {
                        // ignore, will show up when we try to actually read the mappings
                    }
                }
            } else {
                // the best we can do
                byte[] bytes = new byte[32];
                new Random().nextBytes(bytes);
                digest.update(bytes);
            }
        }
    }
}
