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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A result type for Java.
 * The API is highly inspired by Rust's <a href="https://doc.rust-lang.org/std/result/enum.Result.html">Result type</a>
 * and {@link Optional}.
 * <p>
 * The purpose of this type is to provide for building safe APIs where the outcome might be successful or failure.
 * "Safe" in this context means that possible failures have to be considered and dealt with.
 * Usually failure in Java is conveyed via exceptions - either checked or runtime.
 * Runtime exceptions have the disadvantage that it is easy to ignore them because they often are not documented at all
 * and even if they are documented: documentation is not always read carefully.
 * Checked exceptions are cumbersome to work with because all exception handling forces to leave the regular program
 * flow.
 * <p>
 * {@code Result} offers a more lightweight and more flexible solution.
 * It is a regular plain object which can be handled completely in-band. The value of possible failure is not
 * necessarily a {@code Throwable} but any arbitrary type.
 * <p>
 * An additional benefit of using {@code Result} as return type is that it prevents {@code null}.
 * Neither the success value nor the error value are allowed to be {@code null}.
 * Instead, for APIs that are allowed to return nothing, it is highly recommended to use an empty {@code Optional}.
 *
 * @param <S> type of the success value.
 * @param <E> type of the error value.
 */
public sealed interface Result<S, E> extends Iterable<S> permits Ok, Err {

    /**
     * Creates an instance of {@code Ok} which wraps the specified argument.
     *
     * @param successValue the value to be wrapped by the returned Ok.
     * @return the instance.
     * @param <S> type of the success value.
     * @param <E> type of the error value.
     * @throws NullPointerException if {@code successValue} is {@code null}.
     * @see Ok#of(Object)
     */
    static <S, E> Ok<S, E> ok(final S successValue) {
        return Ok.of(successValue);
    }

    /**
     * Creates an instance of {@code Err} which wraps the specified argument.
     *
     * @param errorValue the value to be wrapped by the returned Err.
     * @return the instance.
     * @param <S> type of the success value.
     * @param <E> type of the error value.
     * @throws NullPointerException if {@code errorValue} is {@code null}.
     * @see Err#of(Object)
     */
    static <S, E> Err<S, E> err(final E errorValue) {
        return Err.of(errorValue);
    }

    /**
     * Creates an instance of {@code Result} by trying to apply the specified supplying function.
     * The returned Result is an {@link Ok} which contains the produced value of the supplying function, if successful.
     * Any {@code Throwable} which might occur by applying the supplying function gets caught and wrapped in an
     * {@link Err}.
     *
     * @param supplyingFunction a supplying function to be safely applied.
     * @return the Result instance.
     * @param <S> the type of the success value if applying {@code supplyingFunction} was successful.
     * @throws NullPointerException if {@code supplyingFunction} is {@code null}.
     */
    @SuppressWarnings("java:S1181")
    static <S> Result<S, Throwable> tryToApply(final Supplier<S> supplyingFunction) {
        Objects.requireNonNull(supplyingFunction, "supplyingFunction");
        try {
            return ok(supplyingFunction.get());
        } catch (final Throwable throwable) {
            return err(throwable);
        }
    }

    /**
     * Indicates whether this result is {@link Ok}.
     *
     * @return {@code true} if this is {@code Ok}, {@code false} else.
     */
    boolean isOk();

    /**
     * If a success value is present, performs the specified action with that value, else do nothing.
     *
     * @param successValueAction the action to be performed, if a success value is present.
     * @throws NullPointerException if a success value is present and {@code successValueAction} is {@code null}.
     */
    void ifOk(Consumer<S> successValueAction);

    /**
     * Indicates whether this result is {@link Err}.
     *
     * @return {@code true} if this is {@code Err}, {@code false} else.
     */
    public default boolean isErr() {
        return !isOk();
    }

    /**
     * If an error value is present, performs the specified action with that value, else do nothing.
     *
     * @param errorValueAction the action to be performed, if an error value is present.
     * @throws NullPointerException if an error value is present and {@code errorValueAction} is {@code null}.
     */
    void ifErr(Consumer<E> errorValueAction);

    /**
     * Converts this result into an {@link Optional} which contains the success value, if present, discarding the
     * error value.
     *
     * @return this result as {@code Optional}.
     */
    Optional<S> ok();

    /**
     * If present, returns the success value, otherwise returns the specified alternative success value.
     *
     * @param alternativeSuccessValue the value to be returned, if no success value is present.
     * @return the success value, if present, otherwise {@code alternativeSuccessValue}.
     */
    S orElse(S alternativeSuccessValue);

    /**
     * If present, returns the success value, otherwise returns the result produced by the specified supplying function.
     *
     * @param alternativeSuccessValueSupplier a supplying function that produces the value to be returned, if no success
     * value is present.
     * @return the success value, if present, otherwise the result {@code alternativeSuccessValueSupplier} produces.
     */
    S orElseGet(Supplier<S> alternativeSuccessValueSupplier);

    /**
     * If present, returns the success value, else throws an exception.
     * The thrown exception is determined as follows:
     * <ul>
     *     <li>The error value itself, if it is a {@link RuntimeException} or an {@link Error}.</li>
     *     <li>
     *         A {@code NoSuchElementException} with the message of the error value and the error value as cause,
     *         if the error value is another {@link Throwable}.
     *     </li>
     *     <li>
     *         A {@code NoSuchElementException} with a generic detail message for any other type of error value.
     *     </li>
     * </ul>
     *
     * @return the success value, if present.
     */
    S orElseThrow();

    /**
     * If present, returns the success value, else throws the {@code Throwable} produced by the specified supplying
     * function.
     *
     * @param throwableSupplier a supplying function that produces a Throwable to be thrown, if no success value is
     * present.
     * @return the success value, if present.
     * @param <X> the type of the Throwable {@code throwableSupplier} produces.
     * @throws NullPointerException if no success value is present and {@code throwableSupplier} is {@code null}.
     * @throws X if no success value is present.
     */
    <X extends Throwable> S orElseThrow(Supplier<X> throwableSupplier) throws X;

    /**
     * Converts this result into an {@link Optional} which contains the error value, if present, discarding the
     * success value.
     *
     * @return this result as {@code Optional}.
     */
    Optional<E> err();

    /**
     * Creates a new {@code Result} by applying the specified function to a contained success value, leaving an error
     * value untouched.
     *
     * @param mappingFunction the function for mapping the success value.
     * @return a new Result containing the mapped success value, leaving the error value unchanged.
     * @param <U> the new type of the success value after mapping.
     * @throws NullPointerException if a success value is present and {@code mappingFunction} is {@code null}.
     */
    <U> Result<U, E> map(Function<S, U> mappingFunction);

    /**
     * Creates a new {@code Result} by applying the specified function to a contained error value, leaving a success
     * value untouched.
     *
     * @param mappingFunction the function for mapping the error value.
     * @return a new Result containing the mapped error value, leaving the success value unchanged.
     * @param <F> the new type of the error value after mapping.
     * @throws NullPointerException if an error value is present and {@code mappingFunction} is {@code null}.
     */
    <F> Result<S, F> mapErr(Function<E, F> mappingFunction);

    /**
     * Returns a {@code Stream} with the possibly contained value.
     *
     * @return if present as {@code Stream} containing success value, otherwise an empty stream.
     */
    Stream<S> stream();

    /**
     * Inverts this {@code Result}, i.e. for the returned Result the error value becomes the success value and vice
     * versa.
     *
     * @return a new Result with inverted value.
     */
    Result<E, S> invert();

}
