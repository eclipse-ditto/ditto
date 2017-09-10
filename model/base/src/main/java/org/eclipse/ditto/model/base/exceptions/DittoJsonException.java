/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.exceptions;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;


/**
 * Exception to adapt {@link JsonRuntimeException}s to {@code DittoRuntimeException} by adding {@link DittoHeaders} and
 * the HTTP status code {@link HttpStatusCode#BAD_REQUEST 400}.
 */
public final class DittoJsonException extends DittoRuntimeException {

    /**
     * Fallback Error code of this exception.
     */
    public static final String FALLBACK_ERROR_CODE = "json.invalid";

    private static final long serialVersionUID = -6003501868758251973L;

    /**
     * Constructs a new {@code DittoJsonException} by wrapping the specified {@link JsonRuntimeException} or
     * {@link RuntimeException} and adding empty command headers.
     *
     * @param toWrap the {@link JsonRuntimeException} or {@code RuntimeException} to be wrapped.
     */
    public DittoJsonException(final RuntimeException toWrap) {
        super(
                (toWrap instanceof JsonRuntimeException)
                        ? ((JsonException) toWrap).getErrorCode()
                        : FALLBACK_ERROR_CODE,
                HttpStatusCode.BAD_REQUEST, DittoHeaders.empty(), toWrap.getMessage(),
                (toWrap instanceof JsonRuntimeException)
                        ? ((JsonException) toWrap).getDescription().orElse(null)
                        : null,
                toWrap.getCause(),
                (toWrap instanceof JsonRuntimeException)
                        ? ((JsonException) toWrap).getHref().orElse(null)
                        : null
        );
    }

    /**
     * Constructs a new {@code DittoJsonException} by wrapping the specified {@link JsonRuntimeException} or
     * {@link RuntimeException} and adding the given command headers.
     *
     * @param toWrap the {@link JsonRuntimeException} or {@code RuntimeException} to be wrapped.
     * @param dittoHeaders the command headers to be added.
     */
    public DittoJsonException(final RuntimeException toWrap, final DittoHeaders dittoHeaders) {
        super(
                toWrap instanceof JsonRuntimeException ? ((JsonRuntimeException) toWrap).getErrorCode() :
                        FALLBACK_ERROR_CODE,
                HttpStatusCode.BAD_REQUEST, dittoHeaders, toWrap.getMessage(),
                toWrap instanceof JsonRuntimeException ? ((JsonRuntimeException) toWrap).getDescription().orElse(null) :
                        null,
                toWrap.getCause(),
                toWrap instanceof JsonRuntimeException ? ((JsonRuntimeException) toWrap).getHref().orElse(null) : null);
    }

    /**
     * Executes the given Function. Executes the given Supplier. An occurring {@link JsonRuntimeException},
     * {@code IllegalArgumentException} or {@code NullPointerException} is caught, wrapped and re-thrown as
     * {@code DittoJsonException}.
     *
     * @param function to function which potentially throws a {@code JsonRuntimeException}.
     * @param input the input to the function.
     * @param <I> the type of the input to the function.
     * @param <T> the type of results the function returns.
     * @return the results of the function.
     * @throws DittoJsonException if a {@code JsonRuntimeException} occurred.
     */
    public static <I, T> T wrapJsonRuntimeException(final Function<I, T> function, final I input) {
        try {
            return function.apply(input);
        } catch (final JsonRuntimeException | IllegalArgumentException | NullPointerException e) {
            throw new DittoJsonException(e);
        }
        // "cr-json" library also throws IllegalArgumentException when for example strings which may not be empty
        // (e.g. keys) are empty
        // "cr-json" library also throws NullPointerException when for example non-nullable objects are null
    }

    /**
     * Executes the given Supplier. An occurring {@link JsonRuntimeException}, {@code IllegalArgumentException} or
     * {@code NullPointerException} is caught, wrapped and re-thrown as {@code DittoJsonException}.
     *
     * @param supplier the supplier which potentially throws a {@code JsonRuntimeException}.
     * @param <T> the type of results the supplier returns.
     * @return the results of the supplier.
     * @throws DittoJsonException if a {@code JsonRuntimeException} occurred.
     */
    public static <T> T wrapJsonRuntimeException(final Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final JsonRuntimeException | IllegalArgumentException | NullPointerException e) {
            throw new DittoJsonException(e);
        }
        // "cr-json" library also throws IllegalArgumentException when for example strings which may not be empty
        // (e.g. keys) are empty
        // "cr-json" library also throws NullPointerException when for example non-nullable objects are null
    }

    /**
     * Executes the given Function with the given argument and DittoHeaders. An occurring {@link
     * JsonRuntimeException}, {@code IllegalArgumentException} or {@code NullPointerException} is caught, wrapped and
     * re-thrown as {@code DittoJsonException} with the given DittoHeaders.
     *
     * @param argument the first argument that is passed to the given {@code function}.
     * @param dittoHeaders the command headers that are applied as second argument to the given {@code function} and
     * that are passed to the {@code DittoJsonException} in case any error is thrown by the {@code function}.
     * @param function the supplier which potentially throws a {@code JsonRuntimeException}.
     * @param <T> the type of the first argument for the given {@code function}.
     * @param <R> the type of results the {@code function} returns.
     * @return the results of the function.
     * @throws DittoJsonException if a {@code JsonRuntimeException} occurred.
     */
    public static <T, R> R wrapJsonRuntimeException(final T argument, final DittoHeaders dittoHeaders,
            final BiFunction<T, DittoHeaders, R> function) {
        try {
            return function.apply(argument, dittoHeaders);
        } catch (final JsonRuntimeException | IllegalArgumentException | NullPointerException e) {
            throw new DittoJsonException(e, dittoHeaders);
        }
        // "cr-json" library also throws IllegalArgumentException when for example strings which may not be empty
        // (e.g. keys) are empty
        // "cr-json" library also throws NullPointerException when for example non-nullable objects are null
    }

}
