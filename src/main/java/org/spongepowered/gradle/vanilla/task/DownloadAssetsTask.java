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

import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.model.AssetIndex;
import org.spongepowered.gradle.vanilla.storage.HttpClientService;
import org.spongepowered.gradle.vanilla.util.DigestUtils;
import org.spongepowered.gradle.vanilla.util.GsonUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public abstract class DownloadAssetsTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getAssetsIndex();

    @OutputDirectory
    public abstract DirectoryProperty getAssetsDirectory();

    @Internal
    public abstract Property<HttpClientService> getHttpClient();

    public DownloadAssetsTask() {
        this.setGroup(Constants.TASK_GROUP);
        this.getOutputs().upToDateWhen(t -> false);
    }

    @TaskAction
    public void execute() {
        // Load assets index from file
        final AssetIndex index;
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(
            this.getAssetsIndex().get().getAsFile()),
            StandardCharsets.UTF_8
        ))) {
            index = GsonUtils.GSON.fromJson(reader, AssetIndex.class);
        } catch (final IOException ex) {
            throw new GradleException("Failed to read asset index", ex);
        }

        final Path assetsDirectory = this.getAssetsDirectory().get().getAsFile().toPath();

        try {
            // Validate existing assets in the output directory
            // Because the same assets directory is used by multiple game versions,
            // we only want to delete assets that fail validation, not assets that
            // are just not present in the index.
            final Map<String, AssetIndex.Asset> toDownload = this.validateAssets(index, assetsDirectory);
            // Then, download any remaining assets
            if (!toDownload.isEmpty()) {
                this.downloadAssets(toDownload, assetsDirectory);
                this.setDidWork(true);
            } else {
                this.setDidWork(false);
            }
        } catch (final IOException | URISyntaxException ex) {
            this.getLogger().error("Failed to download assets", ex);
            throw new GradleException("Failed to download assets");
        }
    }

    /**
     * Validate a directory of assets.
     *
     * @param index the index to validate
     * @param assetsDirectory the directory of assets
     * @return a map of all objects that must be downloaded to create a valid asset state
     * @throws IOException if any error occurs while attempting to traverse the file tree
     */
    private Map<String, AssetIndex.Asset> validateAssets(final AssetIndex index, final Path assetsDirectory) throws IOException {
        final Map<String, AssetIndex.Asset> inputObjects = index.objects();
        if (!Files.exists(assetsDirectory)) {
            return inputObjects;
        }

        final Map<String, String> assetNamesByPath = new HashMap<>();
        for (final Map.Entry<String, AssetIndex.Asset> entry : inputObjects.entrySet()) {
            assetNamesByPath.put(entry.getValue().fileName(), entry.getKey());
        }

        Files.walkFileTree(assetsDirectory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new FileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) { return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) { return FileVisitResult.CONTINUE; }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                // Validate
                final String relativePath = assetsDirectory.relativize(file).toString().replace('\\', '/');
                final @Nullable String assetId = assetNamesByPath.get(relativePath);
                if (assetId != null) {
                    // We can verify
                    final AssetIndex.Asset asset = inputObjects.get(assetId);
                    assert asset != null;

                    try (final InputStream is = Files.newInputStream(file)) {
                        if (DigestUtils.validateSha1(asset.hash(), is)) {
                            assetNamesByPath.remove(relativePath);
                        } else {
                            DownloadAssetsTask.this.getLogger().warn("Failed to validate asset {} (expected hash: {})", assetId, asset.hash());
                        }
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
                // Warn
                DownloadAssetsTask.this.getLogger().warn("Failed to read file attributes for asset {}", file);
                return FileVisitResult.CONTINUE;
            }
        });

        if (assetNamesByPath.isEmpty()) { // All assets were found
            return Collections.emptyMap();
        } else {
            // Only return assets that need to be downloaded
            final Map<String, AssetIndex.Asset> remainingAssets = new ConcurrentHashMap<>(inputObjects);
            remainingAssets.keySet().retainAll(assetNamesByPath.values());
            return remainingAssets;
        }
    }

    private void downloadAssets(final Map<String, AssetIndex.Asset> toDownload, final Path assetsDirectory) throws URISyntaxException, IOException {
        final CloseableHttpAsyncClient client = this.getHttpClient().get().client();

        final HttpHost host = HttpHost.create(Constants.MINECRAFT_RESOURCES_URL);
        final CountDownLatch latch = new CountDownLatch(toDownload.size());
        final Set<Map.Entry<String, Exception>> errors = ConcurrentHashMap.newKeySet();
        for (final Map.Entry<String, AssetIndex.Asset> entry : toDownload.entrySet()) {
            final String fileName = entry.getValue().fileName();
            final Path destinationFile = assetsDirectory.resolve(fileName);
            Files.createDirectories(destinationFile.getParent());
            client.execute(
                SimpleRequestProducer.create(SimpleHttpRequests.get(host, '/' + fileName)),
                HttpClientService.responseToFileValidating(destinationFile, entry.getValue().hash()),
                new FutureCallback<Message<HttpResponse, Path>>() {
                    @Override
                    public void completed(final Message<HttpResponse, Path> result) {
                        latch.countDown();
                        final long count = latch.getCount();
                        if ((count & 0xf)  == 0) {
                            final int total = toDownload.size();
                            DownloadAssetsTask.this.getLogger().lifecycle("{}/{} assets downloaded", total - count, total);
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        errors.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), ex));
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        latch.countDown();
                    }
                }
            );
        }

        try {
            latch.await();
        } catch (final InterruptedException ex) {
            throw new GradleException("Interrupted while downloading files");
        }

        if (!errors.isEmpty()) {
            this.getLogger().error("Some errors occurred while downloading assets:");
            for (final Map.Entry<String, Exception> error : errors) {
                this.getLogger().error("While downloading {}", error.getKey(), error.getValue());
            }

            throw new GradleException("Asset download failed");
        }
        this.getLogger().lifecycle("Successfully downloaded {} assets!", toDownload.size());
    }

}
