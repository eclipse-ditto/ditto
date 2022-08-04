/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.exceptions;

import java.net.URI;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonException;
import org.eclipse.ditto.json.JsonRuntimeException;

/**
 * Exception to adapt {@link JsonRuntimeException}s to {@code DittoRuntimeException} by adding {@link org.eclipse.ditto.base.model.headers.DittoHeaders} and
 * the HTTP status code {@link org.eclipse.ditto.base.model.common.HttpStatus#BAD_REQUEST 400}.
 */
public final class DittoJsonException extends DittoRuntimeException implements GeneralException {

    /**
     * Fallback Error code of this exception.
     */
    public static final String FALLBACK_ERROR_CODE = ERROR_CODE_PREFIX + "json.format.invalid";

    private static final long serialVersionUID = -6003501868758251973L;

    /**
     * Constructs a new {@code DittoJsonException} by wrapping the specified {@link JsonRuntimeException} or
     * {@link RuntimeException} and adding empty command headers.
     *
     * @param toWrap the {@link JsonRuntimeException} or {@code RuntimeException} to be wrapped.
     */
    public DittoJsonException(final RuntimeException toWrap) {
        this(toWrap, DittoHeaders.empty());
    }

    /**
     * Constructs a new {@code DittoJsonException} by wrapping the specified {@link JsonRuntimeException} or
     * {@link RuntimeException} and adding the given command headers.
     *
     * @param toWrap the {@link JsonRuntimeException} or {@code RuntimeException} to be wrapped.
     * @param dittoHeaders the command headers to be added.
     */
    public DittoJsonException(final RuntimeException toWrap, final DittoHeaders dittoHeaders) {
        this(extractErrorCode(toWrap),
                HttpStatus.BAD_REQUEST,
                dittoHeaders,
                toWrap.getMessage(),
                extractDescription(toWrap),
                toWrap.getCause(),
                extractHref(toWrap)
        );
    }

    private DittoJsonException(final String errorCode, final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders, @Nullable final String message, @Nullable final String description,
            @Nullable final Throwable cause, @Nullable final URI href) {
        super(errorCode, httpStatus, dittoHeaders, message, description, cause, href);
    }

    private static String extractErrorCode(final RuntimeException toWrap) {
        return (toWrap instanceof JsonRuntimeException)
                ? ((JsonException) toWrap).getErrorCode()
                : FALLBACK_ERROR_CODE;
    }

    @Nullable
    private static String extractDescription(final RuntimeException toWrap) {
        return (toWrap instanceof JsonRuntimeException)
                ? ((JsonException) toWrap).getDescription().orElse(null)
                : null;
    }

    @Nullable
    private static URI extractHref(final RuntimeException toWrap) {
        return (toWrap instanceof JsonRuntimeException)
                ? ((JsonException) toWrap).getHref().orElse(null)
                : null;
    }

    /**
     * Executes the given Function. An occurring {@link JsonRuntimeException},
     * {@code IllegalArgumentException} or {@code NullPointerException} is caught, wrapped and re-thrown as
     * {@code DittoJsonException}.
     *
     * @param function to function which potentially throws a {@code JsonRuntimeException}.
     * @param input the input to the function.
     * @param <I> the type of the input to the function.
     * @param <T> the type of results the function returns.
     * @return the results of the function.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if a {@code JsonRuntimeException} occurred.
     */
    public static <I, T> T wrapJsonRuntimeException(final Function<I, T> function, final I input) {
        try {
            return function.apply(input);
        } catch (final JsonRuntimeException | IllegalArgumentException | NullPointerException e) {
            throw new DittoJsonException(e);
        }
        // "ditto-json" library also throws IllegalArgumentException when for example strings which may not be empty
        // (e.g. keys) are empty
        // "ditto-json" library also throws NullPointerException when for example non-nullable objects are null
    }

    /**
     * Executes the given Supplier. An occurring {@link JsonRuntimeException}, {@code IllegalArgumentException},
     * {@code UnsupportedOperationException}, or {@code NullPointerException} is caught, wrapped and re-thrown as
     * {@code DittoJsonException}.
     *
     * @param supplier the supplier which potentially throws a {@code JsonRuntimeException}.
     * @param <T> the type of results the supplier returns.
     * @return the results of the supplier.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if a {@code JsonRuntimeException} occurred.
     */
    public static <T> T wrapJsonRuntimeException(final Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final JsonRuntimeException
                | IllegalArgumentException
                | NullPointerException
                | UnsupportedOperationException e) {
            throw new DittoJsonException(e);
        }
        // "ditto-json" library also throws IllegalArgumentException when for example strings which may not be empty
        // (e.g. keys) are empty
        // "ditto-json" library also throws NullPointerException when for example non-nullable objects are null
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
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if a {@code JsonRuntimeException} occurred.
     */
    public static <T, R> R wrapJsonRuntimeException(final T argument, final DittoHeaders dittoHeaders,
            final BiFunction<T, DittoHeaders, R> function) {
        try {
            return function.apply(argument, dittoHeaders);
        } catch (final JsonRuntimeException | IllegalArgumentException | NullPointerException e) {
            throw new DittoJsonException(e, dittoHeaders);
        }
        // "ditto-json" library also throws IllegalArgumentException when for example strings which may not be empty
        // (e.g. keys) are empty
        // "ditto-json" library also throws NullPointerException when for example non-nullable objects are null
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DittoJsonException(getErrorCode(), getHttpStatus(), dittoHeaders, getMessage(),
                getDescription().orElse(null), getCause(), getHref().orElse(null));
    }

}
