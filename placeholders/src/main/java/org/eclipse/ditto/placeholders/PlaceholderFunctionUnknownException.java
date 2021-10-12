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
 * Thrown when a {@link PipelineFunction}s is not known.
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderFunctionUnknownException.ERROR_CODE)
public final class PlaceholderFunctionUnknownException extends DittoRuntimeException
        implements PlaceholderException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "function.unknown";

    private static final String MESSAGE_TEMPLATE = "The function <{0}> is not known.";

    private static final String DESCRIPTION = "Ensure to only use supported functions. " +
            "Check if you maybe just forgot to use parentheses for a function with empty parameters.";

    private static final long serialVersionUID = 4328547364325763271L;

    private PlaceholderFunctionUnknownException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a builder of this exception.
     *
     * @param functionExpression the expression of the unknown function.
     * @return a builder of this exception with default message.
     */
    public static DittoRuntimeExceptionBuilder<PlaceholderFunctionUnknownException> newBuilder(
            final String functionExpression) {
        return new Builder()
                .message(MessageFormat.format(MESSAGE_TEMPLATE, functionExpression))
                .description(DESCRIPTION);
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
    public static PlaceholderFunctionUnknownException fromJson(final JsonObject jsonObject,
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
            extends DittoRuntimeExceptionBuilder<PlaceholderFunctionUnknownException> {

        private Builder() {description(DESCRIPTION);}

        @Override
        protected PlaceholderFunctionUnknownException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PlaceholderFunctionUnknownException(dittoHeaders, message, description, cause, href);
        }
    }

}
