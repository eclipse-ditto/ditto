/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.utils.result;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * {@code Ok} represents a successful result.
 * It wraps the success value.
 */
public final class Ok<T, E> implements Result<T, E> {

    private final T value;

    private Ok(final T value) {
        this.value = value;
    }

    /**
     * Returns an instance of {@code Ok} which wraps the specified argument.
     * 
     * @param value the value to be wrapped by the returned Ok.
     * @return the instance.
     * @param <T> type of the success value.
     * @param <E> type of the error value.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    public static <T, E> Ok<T, E> of(final T value) {
        return new Ok<>(Objects.requireNonNull(value, "value"));
    }

    /**
     * Returns the value of this Ok.
     *
     * @return the value.
     */
    public T getSuccessValue() {
        return value;
    }

    @Override
    public boolean isOk() {
        return true;
    }

    @Override
    public void ifOk(final Consumer<T> successValueAction) {
        Objects.requireNonNull(successValueAction, "successValueAction");
        successValueAction.accept(value);
    }

    @Override
    public void ifErr(final Consumer<E> errorValueAction) {
        // Nothing to do here.
    }

    @Override
    public Optional<T> ok() {
        return Optional.of(value);
    }

    @Override
    public T orElse(final T alternativeSuccessValue) {
        return value;
    }

    @Override
    public T orElseGet(final Supplier<T> alternativeSuccessValueSupplier) {
        return value;
    }

    @Override
    public T orElseThrow() {
        return value;
    }

    @Override
    public <X extends Throwable> T orElseThrow(final Supplier<X> throwableSupplier) throws X {
        return value;
    }

    @Override
    public Optional<E> err() {
        return Optional.empty();
    }

    @Override
    public <U> Ok<U, E> map(final Function<T, U> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        return Ok.of(mappingFunction.apply(value));
    }

    @Override
    public <F> Result<T, F> mapErr(final Function<E, F> mappingFunction) {
        return Ok.of(value);
    }

    @Override
    public Stream<T> stream() {
        return Stream.of(value);
    }

    @Override
    public Err<E, T> invert() {
        return Err.of(value);
    }

    @Override
    public Iterator<T> iterator() {
        return new SingleElementIterator<>(value);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var ok = (Ok<?, ?>) o;
        return Objects.equals(value, ok.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "value=" + value +
                "]";
    }

    @NotThreadSafe
    private static final class SingleElementIterator<T> implements Iterator<T> {

        private final T element;
        private boolean endReached;

        private SingleElementIterator(final T element) {
            this.element = element;
            endReached = false;
        }

        @Override
        public boolean hasNext() {
            return !endReached;
        }

        @Override
        public T next() {
            if (endReached) {
                throw new NoSuchElementException("No more elements to iterate over.");
            }

            try {
                return element;
            } finally {
                endReached = true;
            }
        }

    }

}
