package org.spongepowered.vanilla.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.spongepowered.vanilla.gradle.model.Library;
import org.spongepowered.vanilla.gradle.model.Version;
import org.spongepowered.vanilla.gradle.model.VersionDescriptor;
import org.spongepowered.vanilla.gradle.model.VersionManifestV2;

import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;

public abstract class MinecraftExtension {

    private final Property<String> version;
    private final Property<MinecraftPlatform> platform;

    private VersionManifestV2 versionManifest;
    private VersionDescriptor versionDescriptor;
    private Version targetVersion;

    private final DirectoryProperty librariesDirectory;
    private final DirectoryProperty minecraftLibrariesDirectory;

    @Inject
    public MinecraftExtension(final ObjectFactory factory) {
        this.version = factory.property(String.class);
        this.platform = factory.property(MinecraftPlatform.class);
        this.librariesDirectory = factory.directoryProperty();
        this.minecraftLibrariesDirectory = factory.directoryProperty();
    }

    protected Property<String> version() {
        return this.version;
    }

    public void version(final String version) {
        this.version.set(version);
    }

    protected Property<MinecraftPlatform> platform() {
        return this.platform;
    }

    public void platform(final MinecraftPlatform platform) {
        this.platform.set(platform);
    }

    protected DirectoryProperty librariesDirectory() {
        return this.librariesDirectory;
    }

    protected DirectoryProperty minecraftLibrariesDirectory() {
        return this.minecraftLibrariesDirectory;
    }

    protected VersionManifestV2 versionManifest() {
        return this.versionManifest;
    }

    protected VersionDescriptor versionDescriptor() {
        return this.versionDescriptor;
    }

    protected Version targetVersion() {
        return this.targetVersion;
    }

    protected void configure(final Project project) {
        final Path gradleHomeDirectory = project.getGradle().getGradleUserHomeDir().toPath();
        final Path cacheDirectory = gradleHomeDirectory.resolve(Constants.CACHES);
        final Path rootDirectory = cacheDirectory.resolve(Constants.NAME);
        final Path librariesDirectory = rootDirectory.resolve(Constants.LIBRARIES);
        this.librariesDirectory.set(librariesDirectory.toFile());
        this.minecraftLibrariesDirectory.set(librariesDirectory.resolve("net").resolve("minecraft").toFile());

        try {
            this.versionManifest = VersionManifestV2.load();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void determineVersion() {
        this.versionDescriptor = this.versionManifest.findDescriptor(this.version.get()).orElseThrow(() -> new RuntimeException(String.format("No version "
            + "found for '%s' in the manifest!", this.version.get())));
    }

    protected void downloadManifest() {
        try {
            this.targetVersion = this.versionDescriptor.toVersion();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void createMinecraftClasspath(final Project project) {
        final Configuration minecraftClasspath = project.getConfigurations().create("minecraftClasspath");
        minecraftClasspath.withDependencies(a -> {
            for (final Library library : this.targetVersion.libraries()) {
                a.add(project.getDependencies().create(library.name()));
            }
        });
    }
}
