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
package org.eclipse.ditto.model.placeholders;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown when a the placeholder functions in a {@link Pipeline} get too complex (e.g. too many chained function calls)
 * in order to be executed safely by the backend.
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderFunctionTooComplexException.ERROR_CODE)
public final class PlaceholderFunctionTooComplexException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "placeholder.function.too.complex";

    private static final String MESSAGE = "The placeholder functions are not accepted as they are too complex.";

    private static final String DESCRIPTION_TEMPLATE = "Please reduce the complexity (e.g. amount of chained functions below the maximum of ''{0}'') of the functions.";

    private static final long serialVersionUID = -861944566923057294L;

    private PlaceholderFunctionTooComplexException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
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
     */
    public static PlaceholderFunctionTooComplexException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        // deserialize message and description for delivery to client.
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(jsonObject.getValueOrThrow(JsonFields.MESSAGE))
                .description(jsonObject.getValue(JsonFields.DESCRIPTION).orElse(DESCRIPTION_TEMPLATE))
                .build();
    }

    /**
     * Returns a mutable builder for this exception.
     *
     * @return the builder.
     */
    public DittoRuntimeExceptionBuilder<PlaceholderFunctionTooComplexException> toBuilder() {
        return new Builder()
                .dittoHeaders(getDittoHeaders())
                .message(getMessage())
                .description(getDescription().orElse(DESCRIPTION_TEMPLATE))
                .cause(getCause())
                .href(getHref().orElse(null));
    }

    /**
     * A mutable builder with a fluent API.
     */
    @NotThreadSafe
    private static final class Builder
            extends DittoRuntimeExceptionBuilder<PlaceholderFunctionTooComplexException> {

        private Builder() {}

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
