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
package org.spongepowered.gradle.vanilla.renamer.spi;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Objects;

/**
 * Exception that can be thrown when a transformer is being created,
 * if invalid data is provided.
 *
 * @since 0.2.1
 */
public class TransformerProvisionException extends Exception {

    private static final long serialVersionUID = -2631263839948152166L;

    private String transformerId = "<unknown>";

    /**
     * Create a new exception with a detail message but no cause.
     *
     * @param message the message
     * @since 0.2.1
     */
    public TransformerProvisionException(final String message) {
        super(message);
    }

    /**
     * Create a new exception with a detail message and cause
     *
     * @param message the message
     * @param cause the cause
     * @since 0.2.1
     */
    public TransformerProvisionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Get the ID of the transformer that failed to be created.
     *
     * @return the transformer ID
     * @since 0.2.1
     */
    @NonNull String transformerId() {
        return this.transformerId;
    }

    void initId(final String id) {
        this.transformerId = Objects.requireNonNull(id, "id");
    }

}
