package org.spongepowered.vanilla.gradle.model;

import org.spongepowered.vanilla.gradle.util.GsonUtils;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.time.ZonedDateTime;

public final class VersionDescriptor implements Serializable {

    private String id;
    private VersionClassifier type;
    private URL url;
    private ZonedDateTime time;
    private ZonedDateTime releaseTime;
    private String sha1;
    private int complianceLevel;

    public String id() {
        return this.id;
    }

    public VersionClassifier type() {
        return this.type;
    }

    public URL url() {
        return this.url;
    }

    public ZonedDateTime time() {
        return this.time;
    }

    public ZonedDateTime releaseTime() {
        return this.releaseTime;
    }

    public String sha1() {
        return this.sha1;
    }

    public int complianceLevel() {
        return this.complianceLevel;
    }

    public Version toVersion() throws IOException {
        if (this.url == null) {
            throw new IllegalStateException("No URL has been specified for a version descriptor!");
        }

        return GsonUtils.parseFromJson(this.url, Version.class);
    }
}
