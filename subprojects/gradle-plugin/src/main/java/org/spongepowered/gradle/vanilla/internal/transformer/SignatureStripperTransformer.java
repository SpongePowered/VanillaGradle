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
package org.spongepowered.gradle.vanilla.internal.transformer;

import net.minecraftforge.fart.api.Transformer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

final class SignatureStripperTransformer implements Transformer {
    public static final SignatureStripperTransformer INSTANCE = new SignatureStripperTransformer();

    private SignatureStripperTransformer() {
    }

    private static final Attributes.Name SHA_256_DIGEST = new Attributes.Name("SHA-256-Digest");
    @Override
    public ManifestEntry process(final ManifestEntry entry) {
        // Remove all signature entries
        try {
            final Manifest manifest = new Manifest(new ByteArrayInputStream(entry.getData()));
            boolean found = false;
            for (final Iterator<Map.Entry<String, Attributes>> it = manifest.getEntries().entrySet().iterator(); it.hasNext();) {
                final Map.Entry<String, Attributes> section = it.next();
                if (section.getValue().remove(SignatureStripperTransformer.SHA_256_DIGEST) != null) {
                    if (section.getValue().isEmpty()) {
                        it.remove();
                    }
                    found = true;
                }
            }
            if (found) {
                try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                    manifest.write(os);
                    return ManifestEntry.create(entry.getTime(), os.toByteArray());
                }
            }
        } catch (final IOException ex) {
            // no-op, todo log?
        }
        return entry;
    }

    @Override
    public ResourceEntry process(final ResourceEntry entry) {
        if (entry.getName().startsWith("META-INF/")) {
            if (entry.getName().endsWith(".RSA")
            || entry.getName().endsWith(".SF")) {
                return null;
            }
        }
        return entry;
    }
}
