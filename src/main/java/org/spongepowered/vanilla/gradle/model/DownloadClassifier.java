package org.spongepowered.vanilla.gradle.model;

import com.google.gson.annotations.SerializedName;

public enum DownloadClassifier {
    @SerializedName("client")
    CLIENT,
    @SerializedName("client_mappings")
    CLIENT_MAPPINGS,
    @SerializedName("server")
    SERVER,
    @SerializedName("server_mappings")
    SERVER_MAPPINGS
}
