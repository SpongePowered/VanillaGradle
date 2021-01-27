/*
 * This file is part of VanillaGradle, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.gradle.vanilla.model;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Version implements Serializable {

    private Arguments arguments;
    private AssetIndex assetIndex;
    private String assets;
    private int complianceLevel;
    private Map<DownloadClassifier, Download> downloads;
    private String id;
    private List<Library> libraries;
    private JsonObject logging;
    private String mainClass;
    private int minimumLauncherVersion;
    private ZonedDateTime releaseTime;
    private ZonedDateTime time;
    private VersionClassifier type;

    public Arguments arguments() {
        return this.arguments;
    }

    public AssetIndex assetIndex() {
        return this.assetIndex;
    }

    public String assets() {
        return this.assets;
    }

    public int complianceLevel() {
        return this.complianceLevel;
    }

    public Map<DownloadClassifier, Download> downloads() {
        return this.downloads;
    }

    public String id() {
        return this.id;
    }

    public List<Library> libraries() {
        return this.libraries;
    }

    public JsonObject logging() {
        return this.logging;
    }

    public String mainClass() {
        return this.mainClass;
    }

    public int minimumLauncherVersion() {
        return this.minimumLauncherVersion;
    }

    public ZonedDateTime releaseTime() {
        return this.releaseTime;
    }

    public ZonedDateTime time() {
        return this.time;
    }

    public VersionClassifier type() {
        return this.type;
    }

    public Optional<Download> download(final DownloadClassifier classifier) {
        Objects.requireNonNull(classifier, "classifier");

        return Optional.ofNullable(this.downloads.get(classifier));
    }
}
