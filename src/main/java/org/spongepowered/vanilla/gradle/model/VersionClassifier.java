package org.spongepowered.vanilla.gradle.model;

import com.google.gson.annotations.SerializedName;

public enum VersionClassifier {
    @SerializedName("release")
    RELEASE,
    @SerializedName("snapshot")
    SNAPSHOT,
    @SerializedName("old_beta")
    OLD_BETA,
    @SerializedName("old_alpha")
    OLD_ALPHA
}
