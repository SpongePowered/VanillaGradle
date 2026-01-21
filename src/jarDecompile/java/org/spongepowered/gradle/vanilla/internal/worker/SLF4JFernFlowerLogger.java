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
package org.spongepowered.gradle.vanilla.internal.worker;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.slf4j.Logger;

public final class SLF4JFernFlowerLogger extends IFernflowerLogger {

    private final Logger logger;

    public SLF4JFernFlowerLogger(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public void writeMessage(final String message, final Severity severity) {
        switch (severity) {
            case INFO:
                this.logger.info(message);
                break;
            case ERROR:
                this.logger.error(message);
                break;
            case TRACE:
                this.logger.trace(message);
                break;
            case WARN:
                this.logger.warn(message);
                break;
        }
    }

    @Override
    public void writeMessage(final String message, final Severity severity, final Throwable t) {
        switch (severity) {
            case INFO:
                this.logger.info(message, t);
                break;
            case ERROR:
                this.logger.error(message, t);
                break;
            case TRACE:
                this.logger.trace(message, t);
                break;
            case WARN:
                this.logger.warn(message, t);
                break;
        }
    }
}
