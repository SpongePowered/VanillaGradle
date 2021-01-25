package org.spongepowered.vanilla.gradle.model;

import java.io.Serializable;
import java.net.URL;

import javax.annotation.Nullable;

public final class Download implements Serializable {

    private String path;
    private String sha1;
    private int size;
    private URL url;

    @Nullable
    public String path() {
        return this.path;
    }

    public String sha1() {
        return this.sha1;
    }

    public int size() {
        return this.size;
    }

    public URL url() {
        return this.url;
    }
}
