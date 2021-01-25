package org.spongepowered.vanilla.gradle.model;

import java.io.Serializable;
import java.net.URL;

public final class AssetIndex implements Serializable {

    private String id;
    private String sha1;
    private int size;
    private int totalSize;
    private URL url;

    public String id() {
        return this.id;
    }

    public String sha1() {
        return this.sha1;
    }

    public int size() {
        return this.size;
    }

    public int totalSize() {
        return this.totalSize;
    }

    public URL url() {
        return this.url;
    }
}
