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
package org.spongepowered.gradle.vanilla.resolver;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.URL;

/**
 * An exception thrown for any response code except {@code 200}.
 */
public class HttpErrorResponseException extends IOException {

    @Serial
    private static final long serialVersionUID = -1L;

    private final URI source;
    private final int errorCode;

    public HttpErrorResponseException(final URI source, final int errorCode, final String message) {
        super(message);
        this.source = source;
        this.errorCode = errorCode;
    }

    /**
     * The {@link URL} that a connection was made to.
     *
     * @return the source url
     */
    public URI source() {
        return this.source;
    }

    /**
     * The error code returned by the server.
     *
     * @return the error code
     */
    public int errorCode() {
        return this.errorCode;
    }

    @Override
    public String getMessage() {
        return this.errorCode + ": " + super.getMessage() + " (at " + this.source + ")";
    }

}
