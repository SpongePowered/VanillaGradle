package org.spongepowered.vanilla.gradle.model;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Version implements Serializable {

    private Arguments arguments;
    private AssetIndex assetIndex;
    private String assets;
    private int complianceLevel;
    private Map<DownloadClassifier, Download> downloads;
    private String id;
    private List<Library> libraries;
    private JsonObject logging;
    private String mainClass;
    private int minimumLauncherVersion;
    private ZonedDateTime releaseTime;
    private ZonedDateTime time;
    private VersionClassifier type;

    public Arguments arguments() {
        return this.arguments;
    }

    public AssetIndex assetIndex() {
        return this.assetIndex;
    }

    public String assets() {
        return this.assets;
    }

    public int complianceLevel() {
        return this.complianceLevel;
    }

    public Map<DownloadClassifier, Download> downloads() {
        return this.downloads;
    }

    public String id() {
        return this.id;
    }

    public List<Library> libraries() {
        return this.libraries;
    }

    public JsonObject logging() {
        return this.logging;
    }

    public String mainClass() {
        return this.mainClass;
    }

    public int minimumLauncherVersion() {
        return this.minimumLauncherVersion;
    }

    public ZonedDateTime releaseTime() {
        return this.releaseTime;
    }

    public ZonedDateTime time() {
        return this.time;
    }

    public VersionClassifier type() {
        return this.type;
    }

    public Optional<Download> download(final DownloadClassifier classifier) {
        Objects.requireNonNull(classifier, "classifier");

        return Optional.ofNullable(this.downloads.get(classifier));
    }
}
