package org.spongepowered.vanilla.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
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
    private final DirectoryProperty mappingsDirectory;
    private final DirectoryProperty remapDirectory;

    @Inject
    public MinecraftExtension(final ObjectFactory factory) {
        this.version = factory.property(String.class);
        this.platform = factory.property(MinecraftPlatform.class).convention(MinecraftPlatform.SERVER);
        this.librariesDirectory = factory.directoryProperty();
        this.minecraftLibrariesDirectory = factory.directoryProperty();
        this.mappingsDirectory = factory.directoryProperty();
        this.remapDirectory = factory.directoryProperty();
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

    protected DirectoryProperty minecraftLibrariesDirectory() {
        return this.minecraftLibrariesDirectory;
    }

    protected DirectoryProperty mappingsDirectory() {
        return this.mappingsDirectory;
    }

    protected DirectoryProperty remapDirectory() {
        return this.remapDirectory;
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
        this.mappingsDirectory.set(rootDirectory.resolve(Constants.MAPPINGS).toFile());
        this.remapDirectory.set(rootDirectory.resolve(Constants.REMAP).toFile());

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
            for (final MinecraftSide side : this.platform.get().activeSides()) {
                side.applyLibraries(
                    name -> a.add(project.getDependencies().create(name.group() + ':' + name.artifact() + ':' + name.version())),
                    this.targetVersion.libraries()
                );
            }
        });

        project.getConfigurations().named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).configure(path -> path.extendsFrom(minecraftClasspath));
        project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).configure(path -> path.extendsFrom(minecraftClasspath));
    }
}
