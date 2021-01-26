package org.spongepowered.vanilla.gradle.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.spongepowered.vanilla.gradle.model.GroupArtifactVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Objects;

public final class GsonUtils {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ZonedDateTime.class, GsonSerializers.ZDT)
            .registerTypeAdapter(GroupArtifactVersion.class, GsonSerializers.GAV)
            .create();

    public static <T> T parseFromJson(final URL url, final Class<T> type) throws IOException {
        Objects.requireNonNull(url, "url");

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return GsonUtils.GSON.fromJson(reader, type);
        }
    }

    private GsonUtils() {
    }
}
