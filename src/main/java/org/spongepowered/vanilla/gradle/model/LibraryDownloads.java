package org.spongepowered.vanilla.gradle.model;

import java.io.Serializable;
import java.util.Map;

public final class LibraryDownloads implements Serializable {

    private Download artifact;
    private Map<String, Download> classifiers;

    public Download artifact() {
        return this.artifact;
    }

    public Map<String, Download> classifiers() {
        return this.classifiers;
    }
}
