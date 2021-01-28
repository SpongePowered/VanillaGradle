package org.spongepowered.gradle.vanilla.worker;

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
