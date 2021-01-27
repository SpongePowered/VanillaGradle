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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.model.AssetIndex;
import org.spongepowered.gradle.vanilla.util.GsonUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.inject.Inject;

public abstract class DownloadAssetsTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getAssetsIndex();

    @OutputDirectory
    public abstract DirectoryProperty getAssetsDirectory();

    @Console
    public abstract Property<Integer> getConnectionCount();

    @Inject
    protected abstract WorkerExecutor getWorkerFactory();

    public DownloadAssetsTask() {
        this.setGroup(Constants.TASK_GROUP);
        this.getConnectionCount().convention(Runtime.getRuntime().availableProcessors());
    }

    @TaskAction
    public void execute() {
        final AssetIndex index;
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(
            this.getAssetsIndex().get().getAsFile()),
            StandardCharsets.UTF_8
        ))) {
            index = GsonUtils.GSON.fromJson(reader, AssetIndex.class);
        } catch (final IOException ex) {
            throw new GradleException("Failed to read asset index", ex);
        }

        // Create groups based on number of workers
        final int workerCount = this.getConnectionCount().get();
        final WorkQueue queue = this.getWorkerFactory().noIsolation();
        final int countPerWorker = (int) Math.ceil(index.objects().size() / (float) workerCount);
        final Iterator<AssetIndex.Asset> assets = index.objects().values().iterator();
        for (int i = 0; i < workerCount; i++) {
            queue.submit(DownloadAssetsAction.class, action -> {
                action.getAssetsDirectory().set(this.getAssetsDirectory());
                final ListProperty<AssetIndex.Asset> elements = action.getAssets();

                // Split work
                int addedCount = 0;
                while (assets.hasNext() && addedCount++ < countPerWorker) {
                    elements.add(assets.next());
                }
            });
        }

        if (assets.hasNext()) {
            throw new IllegalStateException("Finished asset download tasks with leftover assets!");
        }
    }

    interface DownloadAssetsParameters extends WorkParameters {
        DirectoryProperty getAssetsDirectory();
        ListProperty<AssetIndex.Asset> getAssets();
    }

    public static abstract class DownloadAssetsAction implements WorkAction<DownloadAssetsParameters> {
        private static final Logger LOGGER = LoggerFactory.getLogger(DownloadAssetsAction.class);
        private static final int TRIES = 3;

        @Override
        public void execute() {
            final Path destinationDirectory = this.getParameters().getAssetsDirectory().get().getAsFile().toPath();
            for (final AssetIndex.Asset asset : this.getParameters().getAssets().get()) {
                final Path destinationPath = destinationDirectory.resolve(asset.fileName());
                if (Files.exists(destinationPath)) {
                    // TODO: validate
                    continue;
                }

                // Try to download each file
                for (int attempt = 0; attempt < TRIES; attempt++) {
                    try {
                        Files.createDirectories(destinationPath.getParent());
                        final URLConnection conn = new URL(Constants.MINECRAFT_RESOURCES_URL + asset.fileName()).openConnection();
                        try (final InputStream is = conn.getInputStream();
                             final OutputStream os = Files.newOutputStream(destinationPath)) {
                            final byte[] buf = new byte[4096];
                            int read;
                            while ((read = is.read(buf)) != -1) {
                                os.write(buf, 0, read);
                            }
                        }
                        break;
                    } catch (final IOException ex) {
                        ex.printStackTrace();
                        DownloadAssetsAction.LOGGER.warn("Failed to download asset {} (try ({}/{})", asset.hash(), attempt, TRIES);
                    }
                }
            }
        }
    }

}
