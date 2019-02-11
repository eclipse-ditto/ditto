/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.base.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown when a Placeholder expression's syntax was invalid.
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderInvalidException.ERROR_CODE)
public final class PlaceholderInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "placeholder.invalid";

    private static final String MESSAGE_TEMPLATE = "The placeholder ''{0}'' could not be parsed.";

    private static final String DEFAULT_DESCRIPTION = "Please check if the placeholder is correctly defined (e.g. that opening and closing parentheses match).";

    private static final long serialVersionUID = -260947563924055271L;

    private PlaceholderInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a builder of this exception.
     *
     * @param placeholder the placeholder which could not be parsed.
     * @return a builder of this exception with default message.
     */
    public static DittoRuntimeExceptionBuilder<PlaceholderInvalidException> newBuilder(final String placeholder) {
        return new Builder()
                .message(MessageFormat.format(MESSAGE_TEMPLATE, placeholder))
                .description(DEFAULT_DESCRIPTION);
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject This exception in JSON format.
     * @param dittoHeaders Ditto headers.
     * @return Deserialized exception.
     */
    public static PlaceholderInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        // deserialize message and description for delivery to client.
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(jsonObject.getValueOrThrow(JsonFields.MESSAGE))
                .description(jsonObject.getValue(JsonFields.DESCRIPTION).orElse(DEFAULT_DESCRIPTION))
                .build();
    }

    /**
     * Returns a mutable builder for this exception.
     *
     * @return the builder.
     */
    public DittoRuntimeExceptionBuilder<PlaceholderInvalidException> toBuilder() {
        return new Builder()
                .dittoHeaders(getDittoHeaders())
                .message(getMessage())
                .description(getDescription().orElse(DEFAULT_DESCRIPTION))
                .cause(getCause())
                .href(getHref().orElse(null));
    }

    /**
     * A mutable builder with a fluent API.
     */
    @NotThreadSafe
    private static final class Builder extends DittoRuntimeExceptionBuilder<PlaceholderInvalidException> {

        private Builder() {}

        @Override
        protected PlaceholderInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PlaceholderInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
