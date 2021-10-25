/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown when a the placeholder functions in a {@link Pipeline} get too complex (e.g. too many chained function calls)
 * in order to be executed safely by the backend.
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderFunctionTooComplexException.ERROR_CODE)
public final class PlaceholderFunctionTooComplexException extends DittoRuntimeException
        implements PlaceholderException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "function.too.complex";

    private static final String MESSAGE = "The placeholder functions are not accepted as they are too complex.";

    private static final String DESCRIPTION_TEMPLATE = "Please reduce the complexity (e.g. amount of chained functions below the maximum of ''{0}'') of the functions.";

    private static final long serialVersionUID = -861944566923057294L;

    private PlaceholderFunctionTooComplexException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a builder of this exception.
     *
     * @param maxAllowedChainedFunctions the maximum amount of chained function calls which is allowed.
     * @return a builder of this exception with default message.
     */
    public static DittoRuntimeExceptionBuilder<PlaceholderFunctionTooComplexException> newBuilder(
            final int maxAllowedChainedFunctions) {
        return new Builder()
                .message(MESSAGE)
                .description(MessageFormat.format(DESCRIPTION_TEMPLATE, maxAllowedChainedFunctions));
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject This exception in JSON format.
     * @param dittoHeaders Ditto headers.
     * @return Deserialized exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PlaceholderFunctionTooComplexException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        // deserialize message and description for delivery to client.
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API.
     */
    @NotThreadSafe
    private static final class Builder
            extends DittoRuntimeExceptionBuilder<PlaceholderFunctionTooComplexException> {

        private Builder() {description(DESCRIPTION_TEMPLATE);}

        @Override
        protected PlaceholderFunctionTooComplexException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PlaceholderFunctionTooComplexException(dittoHeaders, message, description, cause, href);
        }

    }

}
