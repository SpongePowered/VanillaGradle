package org.spongepowered.vanilla.gradle.model;

import com.google.gson.JsonArray;

import java.io.Serializable;

public final class Arguments implements Serializable {

    private JsonArray game;
    private JsonArray jvm;

    public JsonArray game() {
        return this.game;
    }

    public JsonArray jvm() {
        return this.jvm;
    }
}
