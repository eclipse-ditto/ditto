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
package org.eclipse.ditto.base.model.signals.commands.exceptions;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * This exception indicates that a Solution or user has sent send too many requests in a defined timeframe.
 */
@Immutable
@JsonParsableException(errorCode = GatewayServiceTooManyRequestsException.ERROR_CODE)
public class GatewayServiceTooManyRequestsException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "too.many.requests";

    private static final String DEFAULT_MESSAGE =
            "Your Solution is temporarily blocked. Please try again in a few minutes.";

    private static final String DEFAULT_DESCRIPTION =
            "You have sent too many requests at once. Please wait before trying again.";

    private static final long serialVersionUID = 1164235483383640723L;

    private GatewayServiceTooManyRequestsException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.TOO_MANY_REQUESTS, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code GatewayServiceTooManyRequestsException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayServiceTooManyRequestsException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static GatewayServiceTooManyRequestsException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * A mutable builder for a {@code GatewayServiceTooManyRequestsException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() { return new Builder(); }

    /**
     * Constructs a new {@code GatewayServiceTooManyRequestsException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayServiceTooManyRequestsException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static GatewayServiceTooManyRequestsException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link GatewayServiceTooManyRequestsException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayServiceTooManyRequestsException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        protected GatewayServiceTooManyRequestsException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new GatewayServiceTooManyRequestsException(dittoHeaders, message, description, cause, href);
        }
    }
}
