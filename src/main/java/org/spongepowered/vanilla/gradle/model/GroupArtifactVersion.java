package org.spongepowered.vanilla.gradle.model;

public final class GroupArtifactVersion {
    private final String group;
    private final String artifact;
    private final String version;

    public GroupArtifactVersion(final String group, final String artifact, final String version) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    public String group() {
        return this.group;
    }

    public String artifact() {
        return this.artifact;
    }

    public String version() {
        return this.version;
    }

}
