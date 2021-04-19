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
package org.spongepowered.gradle.vanilla.network;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public enum HashAlgorithm {
    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256"),
    SHA512("SHA-512");

    private final String algorithmName;

    HashAlgorithm(final String algorithmName) {
        this.algorithmName = algorithmName;

    }

    public String digestName() {
        return this.algorithmName;
    }

    public MessageDigest digest() {
        try {
            return MessageDigest.getInstance(this.algorithmName);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to locate digest algorithm for " + this.name(), ex);
        }
    }

    public DigestInputStream validate(final InputStream is) {
        return new DigestInputStream(is, this.digest());
    }

    public boolean validate(final String expectedHash, final Path path) throws IOException {
        try (final InputStream is = Files.newInputStream(path)) {
            return this.validate(expectedHash, is);
        }
    }

    public boolean validate(final String expectedHash, final InputStream stream) throws IOException {
        final MessageDigest digest = this.digest();

        final byte[] buf = new byte[4096];
        int read;

        while ((read = stream.read(buf)) != -1) {
            digest.update(buf,0, read);
        }

        return expectedHash.equals(HashAlgorithm.toHexString(digest.digest()));
    }

    // From http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    public static String toHexString(final byte[] bytes) {
        final char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            final int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HashAlgorithm.HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HashAlgorithm.HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}
