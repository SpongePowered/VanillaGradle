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
package org.spongepowered.gradle.vanilla.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.model.AssetIndex;
import org.spongepowered.gradle.vanilla.model.AssetIndexReference;
import org.spongepowered.gradle.vanilla.network.Downloader;
import org.spongepowered.gradle.vanilla.network.HashAlgorithm;
import org.spongepowered.gradle.vanilla.repository.MinecraftProviderService;
import org.spongepowered.gradle.vanilla.repository.ResolutionResult;
import org.spongepowered.gradle.vanilla.util.GsonUtils;
import org.spongepowered.gradle.vanilla.util.Pair;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public abstract class DownloadAssetsTask extends DefaultTask {

    @OutputDirectory
    public abstract DirectoryProperty getAssetsDirectory();

    @Input
    public abstract Property<String> getTargetVersion();

    @Internal
    public abstract Property<MinecraftProviderService> getMinecraftProvider();

    public DownloadAssetsTask() {
        this.setGroup(Constants.TASK_GROUP);
        this.getOutputs().upToDateWhen(t -> false);
        this.getOutputs().doNotCacheIf("We perform our own up-to-date checking", spec -> true);
    }

    @TaskAction
    public void execute() {
        final Path assetsDirectory = this.getAssetsDirectory().get().getAsFile().toPath();
        final Downloader downloader = this.getMinecraftProvider().get().downloader().withBaseDir(assetsDirectory);

        // Fetch asset index
        this.getLogger().info("Fetching asset index for {}", this.getTargetVersion().get());
        final CompletableFuture<ResolutionResult<AssetIndex>> assets = this.getMinecraftProvider().get().versions().fullVersion(this.getTargetVersion().get())
            .thenCompose(result -> {
                if (!result.isPresent()) {
                    return CompletableFuture.completedFuture(ResolutionResult.notFound());
                }
                final AssetIndexReference ref = result.get().assetIndex();
                return downloader.readStringAndValidate(ref.url(), "indexes/" + ref.id() + ".json", HashAlgorithm.SHA1, ref.sha1())
                    .thenApply(idx -> idx.mapIfPresent((upToDate, contents) -> GsonUtils.GSON.fromJson(contents, AssetIndex.class)));
            });

        final AssetIndex index;
        try {
            index = assets.get()
                .orElseThrow(() -> new InvalidUserDataException("Could not resolve an asset index for version '" + this.getTargetVersion().get() + "'!"));
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GradleException("interrupted");
        } catch (final ExecutionException ex) {
            throw new GradleException("Failed to download asset index", ex.getCause());
        }

        this.getLogger().info("Downloading and verifying {} assets for {}", index.objects().size(), this.getTargetVersion().get());
        final Path objectsDirectory = assetsDirectory.resolve("objects");
        // For every asset in the index, resolve create a future providing the resolution result
        // The asset index will handle resolution
        final Set<CompletableFuture<ResolutionResult<AssetIndex.Asset>>> results = new HashSet<>();
        final Set<Pair<Pair<String, AssetIndex.Asset>, Throwable>> failedAssets = ConcurrentHashMap.newKeySet();

        // Send out all the requests
        for (final Map.Entry<String, AssetIndex.Asset> asset : index.objects().entrySet()) {
            results.add(downloader.downloadAndValidate(
                this.assetUrl(asset.getValue()),
                objectsDirectory.resolve(asset.getValue().fileName()),
                HashAlgorithm.SHA1,
                asset.getValue().hash()
            )
                .thenApply(result -> result.mapIfPresent((upToDate, unused) -> asset.getValue()))
                .exceptionally(err -> {
                    failedAssets.add(Pair.of(Pair.of(asset), err));
                    return null;
                }));
        }

        // Then await them all, see how many had errors,
        // We handle all errors above, so we can use the simple join() here without worrying about a dangling future
        CompletableFuture.allOf(results.toArray(new CompletableFuture<?>[0])).join();

        if (!failedAssets.isEmpty()) {
            this.getLogger().warn("Failed to download the following assets! Client may appear in an unexpected state.");
            for (final Pair<Pair<String, AssetIndex.Asset>, Throwable> asset : failedAssets) {
                this.getLogger().warn("- {}", asset.first().first(), asset.second());
            }
        }

        final ResolutionResult.Statistics stats = results.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(ResolutionResult.statisticCollector());

        if (stats.upToDate() == stats.total()) {
            this.setDidWork(false);
            return; // all up-to-date
        }
        this.setDidWork(true);

        if (stats.notFound() > 0) {
            this.getLogger().warn(
                "A total of {} assets in the {} index could not be found. Results may be incorrect.",
                stats.notFound(),
                this.getTargetVersion().get()
            );
        }

        this.getLogger().lifecycle(
            "Downloaded {} assets (out of {} total, {} already up-to-date",
            stats.found() - stats.upToDate(),
            stats.total(),
            stats.upToDate()
        );
    }

    private URL assetUrl(final AssetIndex.Asset asset) {
        try {
            return new URL("https", Constants.MINECRAFT_RESOURCES_HOST, '/' + asset.fileName());
        } catch (final MalformedURLException ex) {
            throw new IllegalArgumentException("Failed to parse asset URL for " + asset.hash(), ex);
        }
    }

}
