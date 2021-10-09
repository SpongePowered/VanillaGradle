package org.spongepowered.gradle.vanilla.internal.task;

import org.cadixdev.bombe.jar.JarEntryTransformer;

import java.io.IOException;

public interface JarEntryTransformerProvider {
    JarEntryTransformer getJarEntryTransformer() throws IOException;
}
