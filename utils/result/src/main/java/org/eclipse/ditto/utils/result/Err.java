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

/**
 * {@code Err} represents an error result.
 * It wraps the error value.
 */
public final class Err<S, T> implements Result<S, T> {

    private final T value;

    private Err(final T value) {
        this.value = value;
    }

    /**
     * Returns an instance of {@code Err} which wraps the specified argument.
     *
     * @param value the value to be wrapped by the returned Err.
     * @return the instance.
     * @param <S> type of the success value.
     * @param <T> type of the error value.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    public static <S, T> Err<S, T> of(final T value) {
        return new Err<>(Objects.requireNonNull(value, "value"));
    }

    /**
     * Returns the value of this Err.
     *
     * @return the value.
     */
    public T getErrorValue() {
        return value;
    }

    @Override
    public boolean isOk() {
        return false;
    }

    @Override
    public void ifOk(final Consumer<S> successValueAction) {
        // Nothing to do here.
    }

    @Override
    public void ifErr(final Consumer<T> errorValueAction) {
        Objects.requireNonNull(errorValueAction, "errorValueAction");
        errorValueAction.accept(value);
    }

    @Override
    public Optional<S> ok() {
        return Optional.empty();
    }

    @Override
    public S orElse(final S alternativeSuccessValue) {
        return Objects.requireNonNull(alternativeSuccessValue, "alternativeSuccessValue");
    }

    @Override
    public S orElseGet(final Supplier<S> alternativeSuccessValueSupplier) {
        Objects.requireNonNull(alternativeSuccessValueSupplier, "alternativeSuccessValueSupplier");
        return alternativeSuccessValueSupplier.get();
    }

    @Override
    public S orElseThrow() {
        if (value instanceof RuntimeException runtimeException) {
            throw runtimeException;
        } else if (value instanceof Error error) {
            throw error;
        } else if (value instanceof Throwable throwable) {
            throw new NoSuchElementException(throwable.getMessage(), throwable);
        } else {
            throw new NoSuchElementException("No success value for an Err.");
        }
    }

    @Override
    public <X extends Throwable> S orElseThrow(final Supplier<X> throwableSupplier) throws X {
        Objects.requireNonNull(throwableSupplier, "throwableSupplier");
        throw throwableSupplier.get();
    }

    @Override
    public Optional<T> err() {
        return Optional.of(value);
    }

    @Override
    public <U> Result<U, T> map(final Function<S, U> mappingFunction) {
        return Err.of(value);
    }

    @Override
    public <F> Err<S, F> mapErr(final Function<T, F> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        return Err.of(mappingFunction.apply(value));
    }

    @Override
    public Stream<S> stream() {
        return Stream.empty();
    }

    @Override
    public Ok<T, S> invert() {
        return Ok.of(value);
    }

    @Override
    public Iterator<S> iterator() {
        return new EmptyIterator<>();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var err = (Err<?, ?>) o;
        return Objects.equals(value, err.value);
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

    private static final class EmptyIterator<T> implements Iterator<T> {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            throw new NoSuchElementException("No success value for an Err.");
        }

    }

}
