package org.spongepowered.vanilla.gradle.model;

import org.spongepowered.vanilla.gradle.Constants;
import org.spongepowered.vanilla.gradle.util.GsonUtils;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class VersionManifestV2 implements Serializable {

    private Map<VersionClassifier, String> latest;
    private List<VersionDescriptor> versions;

    public Map<VersionClassifier, String> latest() {
        return this.latest;
    }

    public List<VersionDescriptor> versions() {
        return this.versions;
    }

    public Optional<VersionDescriptor> findDescriptor(final String id) {
        Objects.requireNonNull(id, "id");

        for (final VersionDescriptor version : this.versions) {
            if (version.id().equals(id)) {
                return Optional.of(version);
            }
        }

        return Optional.empty();
    }

    public static VersionManifestV2 load() throws IOException {
        final URL url = new URL(Constants.API_V2_ENDPOINT);
        return GsonUtils.parseFromJson(url, VersionManifestV2.class);
    }
}
