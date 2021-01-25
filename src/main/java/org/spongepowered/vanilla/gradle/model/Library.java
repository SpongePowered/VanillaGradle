package org.spongepowered.vanilla.gradle.model;

import com.google.gson.JsonElement;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public final class Library implements Serializable {

    private LibraryDownloads downloads;
    private String name;
    private Map<String, String> natives;
    private List<JsonElement> rules;

    public LibraryDownloads downloads() {
        return this.downloads;
    }

    public String name() {
        return this.name;
    }

    public Map<String, String> natives() {
        return this.natives;
    }

    public List<JsonElement> rules() {
        return this.rules;
    }
}
