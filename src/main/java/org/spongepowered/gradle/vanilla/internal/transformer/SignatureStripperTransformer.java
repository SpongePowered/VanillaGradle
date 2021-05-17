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

import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.bombe.jar.JarManifestEntry;
import org.cadixdev.bombe.jar.JarResourceEntry;

import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;

final class SignatureStripperTransformer implements JarEntryTransformer {
    public static final SignatureStripperTransformer INSTANCE = new SignatureStripperTransformer();

    private SignatureStripperTransformer() {
    }

    private static final Attributes.Name SHA_256_DIGEST = new Attributes.Name("SHA-256-Digest");
    @Override
    public JarManifestEntry transform(final JarManifestEntry entry) {
        // Remove all signature entries
        for (final Iterator<Map.Entry<String, Attributes>> it = entry.getManifest().getEntries().entrySet().iterator(); it.hasNext();) {
            final Map.Entry<String, Attributes> section = it.next();
            if (section.getValue().remove(SignatureStripperTransformer.SHA_256_DIGEST) != null) {
                if (section.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
        return entry;
    }

    @Override
    public JarResourceEntry transform(final JarResourceEntry entry) {
        if (entry.getName().startsWith("META-INF")) {
            if (entry.getExtension().equals("RSA")
            || entry.getExtension().equals("SF")) {
                return null;
            }
        }
        return entry;
    }
}
