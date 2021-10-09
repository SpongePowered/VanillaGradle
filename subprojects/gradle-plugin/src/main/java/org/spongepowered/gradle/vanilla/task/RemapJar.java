package org.spongepowered.gradle.vanilla.task;

import org.cadixdev.atlas.jar.JarFile;
import org.cadixdev.atlas.util.CascadingClassProvider;
import org.cadixdev.bombe.asm.analysis.ClassProviderInheritanceProvider;
import org.cadixdev.bombe.asm.jar.ClassProvider;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.jvm.tasks.Jar;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.internal.Constants;
import org.spongepowered.gradle.vanilla.internal.MinecraftExtensionImpl;
import org.spongepowered.gradle.vanilla.internal.repository.MinecraftProviderService;
import org.spongepowered.gradle.vanilla.internal.task.JarEntryTransformerProvider;
import org.spongepowered.gradle.vanilla.internal.task.RemapFilter;
import org.spongepowered.gradle.vanilla.internal.transformer.AtlasTransformers;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolver;
import org.spongepowered.gradle.vanilla.repository.MinecraftResolverImpl;
import org.spongepowered.gradle.vanilla.repository.mappings.MappingsEntry;
import org.spongepowered.gradle.vanilla.resolver.ResolutionResult;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public abstract class RemapJar extends Jar implements JarEntryTransformerProvider {
    private final Property<String> fromMappings;
    private final Property<String> toMappings;
    private FileCollection classpath;
    private JarEntryTransformer cachedTransformer;

    public RemapJar() {
        final Project project = getProject();
        final ObjectFactory objects = project.getObjects();

        fromMappings = objects.property(String.class).convention(project.provider(() -> {
            final MinecraftExtension extension = project.getExtensions().findByType(MinecraftExtension.class);
            Objects.requireNonNull(extension, "Could not find minecraft extension in project");
            return extension.minecraftMappings().get();
        }));
        toMappings = objects.property(String.class);

        with(RemapFilter.createCopySpec(project, this));
    }

    @Input
    public Property<String> getFromMappings() {
        return fromMappings;
    }

    public void fromMappings(MappingsEntry mappings) {
        fromMappings(mappings.getName());
    }

    public void fromMappings(String mappings) {
        fromMappings.set(mappings);
    }

    public void toMappings(MappingsEntry mappings) {
        toMappings(mappings.getName());
    }

    public void toMappings(String mappings) {
        toMappings.set(mappings);
    }

    @Input
    public Property<String> getToMappings() {
        return toMappings;
    }

    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @Internal
    public abstract Property<MinecraftProviderService> getMinecraftProvider();

    @Override
    public synchronized JarEntryTransformer getJarEntryTransformer() throws IOException {
        if (cachedTransformer != null) {
            return cachedTransformer;
        }
        Project project = getProject();
        MinecraftExtensionImpl extension = (MinecraftExtensionImpl) project.getExtensions().findByType(MinecraftExtension.class);
        Objects.requireNonNull(extension, "Could not find minecraft extension in project");
        MinecraftProviderService minecraftProvider = getMinecraftProvider().get();
        minecraftProvider.primeResolver(project, extension.modifiers());
        MinecraftResolverImpl resolver = (MinecraftResolverImpl) minecraftProvider.resolver();
        String minecraftVersion = extension.version().get();
        CompletableFuture<ResolutionResult<MinecraftResolver.MinecraftEnvironment>> envFuture =
                resolver.provide(extension.platform().get(), minecraftVersion, extension.modifiers());
        try {
            resolver.processSyncTasksUntilComplete(envFuture);
        } catch (final ExecutionException ex) {
            throw new GradleException("Failed to remap", ex.getCause());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted");
        }
        ResolutionResult<MinecraftResolver.MinecraftEnvironment> envResult = envFuture.join();
        if (!envResult.isPresent()) {
            throw new IllegalStateException("Could not find Minecraft environment");
        }

        List<FileTree> allFiles = new ArrayList<>();
        getMainSpec().walk(res -> allFiles.add(res.getAllSource()));
        FileCollection actualClasspath = project.files(classpath, allFiles);
        MappingSet mappings = extension.getMappings().getByName(toMappings.get()).convertFrom(
                fromMappings.get(),
                resolver,
                envResult.get(),
                extension.platform().get(),
                resolver.sharedArtifactSupplier(minecraftVersion)
        );
        List<ClassProvider> classProviders = new ArrayList<>();
        for (File file : actualClasspath) {
            if (file.getName().endsWith(".jar")) {
                classProviders.add(new JarFile(file.toPath()));
            }
        }
        classProviders.add(name -> {
            Set<File> files = actualClasspath.getAsFileTree().matching(tree -> tree.include(name + ".class")).getFiles();
            if (files.size() != 1) {
                return null;
            }
            try {
                return Files.readAllBytes(files.iterator().next().toPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return cachedTransformer = AtlasTransformers.remap(mappings, new ClassProviderInheritanceProvider(
                Constants.ASM_VERSION,
                new CascadingClassProvider(classProviders))
        );
    }
}
