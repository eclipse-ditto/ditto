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
 * Thrown when a {@link PipelineFunction}s is not known.
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderFunctionUnknownException.ERROR_CODE)
public final class PlaceholderFunctionUnknownException extends DittoRuntimeException
        implements PlaceholderException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = PlaceholderException.ERROR_CODE_PREFIX + "function.unknown";

    private static final String MESSAGE_TEMPLATE = "The function <{0}> is not known.";

    private static final String DESCRIPTION = "Ensure to only use supported functions. " +
            "Check if you maybe just forgot to use parentheses for a function with empty parameters.";

    private static final long serialVersionUID = 4328547364325763271L;

    private PlaceholderFunctionUnknownException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
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
     */
    public static PlaceholderFunctionUnknownException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        // deserialize message and description for delivery to client.
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(jsonObject.getValueOrThrow(JsonFields.MESSAGE))
                .description(jsonObject.getValue(JsonFields.DESCRIPTION).orElse(DESCRIPTION))
                .build();
    }

    /**
     * Returns a mutable builder for this exception.
     *
     * @return the builder.
     */
    public DittoRuntimeExceptionBuilder<PlaceholderFunctionUnknownException> toBuilder() {
        return new Builder()
                .dittoHeaders(getDittoHeaders())
                .message(getMessage())
                .description(getDescription().orElse(DESCRIPTION))
                .cause(getCause())
                .href(getHref().orElse(null));
    }

    /**
     * A mutable builder with a fluent API.
     */
    @NotThreadSafe
    private static final class Builder
            extends DittoRuntimeExceptionBuilder<PlaceholderFunctionUnknownException> {

        private Builder() {}

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
