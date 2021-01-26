package org.spongepowered.vanilla.gradle;

import org.spongepowered.vanilla.gradle.model.DownloadClassifier;
import org.spongepowered.vanilla.gradle.model.GroupArtifactVersion;
import org.spongepowered.vanilla.gradle.model.Library;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public enum MinecraftSide {
    CLIENT(DownloadClassifier.CLIENT, DownloadClassifier.CLIENT_MAPPINGS) {
        @Override
        void applyLibraries(final Consumer<GroupArtifactVersion> handler, final List<Library> knownLibraries) {
            // Client gets all libraries
            for (final Library library : knownLibraries) {
                handler.accept(library.name());
            }
        }
    },
    SERVER(DownloadClassifier.SERVER, DownloadClassifier.SERVER_MAPPINGS) {
        private final Set<String> packages;

        {
            final Set<String> allowedPackages = new HashSet<>();
            allowedPackages.add("net/minecraft");
            this.packages = Collections.unmodifiableSet(allowedPackages);
        }

        @Override
        void applyLibraries(final Consumer<GroupArtifactVersion> handler, final List<Library> knownLibraries) {
            // TODO: This is kinda ugly, using a hardcoded list
            // Unfortunately Gradle both lets you tweak the metadata of an incoming artifact, and transform the artifact itself, but not both at
            // the same time.
            for (final Library library : knownLibraries) {
                if (!Constants.CLIENT_ONLY_DEPENDENCY_GROUPS.contains(library.name().group())) {
                    handler.accept(library.name());
                }
            }
        }

        @Override
        Set<String> allowedPackages() {
            return this.packages;
        }
    };

    private final DownloadClassifier executableArtifact;
    private final DownloadClassifier mappingsArtifact;

    MinecraftSide(final DownloadClassifier executableArtifact, final DownloadClassifier mappingsArtifact) {
        this.executableArtifact = executableArtifact;
        this.mappingsArtifact = mappingsArtifact;
    }

    final DownloadClassifier executableArtifact() {
        return this.executableArtifact;
    }

    final DownloadClassifier mappingsArtifact() {
        return this.mappingsArtifact;
    }

    abstract void applyLibraries(final Consumer<GroupArtifactVersion> dependencyAccepter, final List<Library> knownLibraries);

    /**
     * Get packages that will remain unfiltered in the jar
     *
     * <p>An empty set allows all packages.</p>
     *
     * @return a set of packages to leave unfiltered, or empty to perform no filtering
     */
    Set<String> allowedPackages() {
        return Collections.emptySet();
    }
}
