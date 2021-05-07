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
package org.spongepowered.gradle.vanilla.repository;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * A container for the result of a resolution operation.
 *
 * <p>This indicates both whether a value was resolved, as well as whether that
 * resolution was an up-to-date response, or required information to be
 * re-processed.</p>
 *
 * @param <V> the result type
 */
public final class ResolutionResult<V> {
    private final @Nullable V result;
    private final boolean upToDate;

    public static <T> ResolutionResult<T> notFound() {
        return new ResolutionResult<>(null, false);
    }

    public static <T> ResolutionResult<T> result(final T result, final boolean upToDate) {
        Objects.requireNonNull(result, "result");
        return new ResolutionResult<>(result, upToDate);
    }

    private ResolutionResult(final @Nullable V result, final boolean upToDate) {
        this.result = result;
        this.upToDate = upToDate;
    }

    /**
     * Assert that this resolution has a result.
     *
     * @return the result
     * @throws NoSuchElementException if no value was present
     */
    public V get() {
        if (this.result == null) {
            throw new NoSuchElementException("result");
        }
        return this.result;
    }

    /**
     * Get a result from this resolution action, if present.
     *
     * @return the result
     */
    public @Nullable V orElseNull() {
        return this.result;
    }

    /**
     * Get a present value from this result, or throw the provided exception.
     *
     * @param exceptionSupplier a supplier providing the exception to throw
     * @param <E> the type of exception to throw
     * @return the value, if present
     * @throws E when a value is not present
     */
    public <E extends Exception> V orElseThrow(final Supplier<E> exceptionSupplier) throws E {
        if (this.result == null) {
            throw exceptionSupplier.get();
        }
        return this.result;
    }

    /**
     * Transform any present value of this resolution result.
     *
     * <p>The up-to-date flag with be preserved from {@code this}.</p>
     *
     * @param transformer the transformer, accepting a value of {@code ({@link #upToDate()}, {@link #get()})}
     * @param <U> the result value type
     * @return a transformed resolution result
     */
    @SuppressWarnings("unchecked")
    public <U> ResolutionResult<U> mapIfPresent(final BiFunction<Boolean, V, U> transformer) {
        if (this.result == null) {
            return (ResolutionResult<U>) this; // safe, no value is present
        } else {
            return ResolutionResult.result(transformer.apply(this.upToDate, this.result), this.upToDate);
        }
    }

    /**
     * Get whether a value is present.
     *
     * @return the value
     */
    public final boolean isPresent() {
        return this.result != null;
    }

    /**
     * Get whether the result was already up to date at resolution time.
     *
     * <p>If this return {@code false}, this indicates that the incoming
     * artifact was out-of-date and any downstream artifacts should be
     * invalidated.</p>
     *
     * <p>Not-found results will never be up-to-date.</p>
     *
     * @return the up-to-date status
     */
    public boolean upToDate() {
        return this.upToDate;
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ResolutionResult<?>)) {
            return false;
        }

        final ResolutionResult<?> that = (ResolutionResult<?>) other;
        return Objects.equals(this.result, that.result)
            && this.upToDate == that.upToDate;
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.result);
        h += (h << 5) + Boolean.hashCode(this.upToDate);
        return h;
    }

    @Override
    public String toString() {
        return "ResolutionResult{"
            + "result=" + this.result
            + ", upToDate=" + this.upToDate
            + "}";
    }

    public static <T> Collector<ResolutionResult<T>, Statistics, Statistics> statisticCollector() {
        return Collector.of(
            Statistics::new,
            (stats, result) -> {
                stats.total++;
                if (result.upToDate()) {
                    stats.upToDate++;
                }
                if (result.isPresent()) {
                    stats.found++;
                } else {
                    stats.notFound++;
                }
            },
            (a, b) -> new Statistics(
                a.upToDate + b.upToDate,
                a.notFound + b.notFound,
                a.found + b.found,
                a.total + b.total
            ),
            Collector.Characteristics.CONCURRENT,
            Collector.Characteristics.UNORDERED
        );
    }

    /**
     * Retrieve statistics about a collection of resolution results.
     */
    public static final class Statistics {
        int upToDate;
        int notFound;
        int found;
        int total;

        Statistics() {
        }

        Statistics(final int upToDate, final int notFound, final int found, final int total) {
            this.upToDate = upToDate;
            this.notFound = notFound;
            this.found = found;
            this.total = total;
        }

        public int upToDate() {
            return this.upToDate;
        }

        public int notFound() {
            return this.notFound;
        }

        public int found() {
            return this.found;
        }

        public int total() {
            return this.total;
        }
    }

}
