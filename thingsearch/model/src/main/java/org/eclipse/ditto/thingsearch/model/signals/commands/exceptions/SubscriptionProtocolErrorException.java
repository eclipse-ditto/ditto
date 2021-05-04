/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.model.signals.commands.exceptions;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.thingsearch.model.ThingSearchException;

/**
 * Error response for subscriptions with no interaction for a long time.
 *
 * @since 1.1.0
 */
@JsonParsableException(errorCode = SubscriptionProtocolErrorException.ERROR_CODE)
public class SubscriptionProtocolErrorException extends DittoRuntimeException implements ThingSearchException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subscription.protocol.error";

    private static final HttpStatus STATUS_CODE = HttpStatus.BAD_REQUEST;

    private SubscriptionProtocolErrorException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a {@code SubscriptionProtocolErrorException}.
     *
     * @param cause the actual protocol error.
     * @param dittoHeaders the Ditto headers.
     * @return the exception.
     */
    public static SubscriptionProtocolErrorException of(final Throwable cause, final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(cause.getMessage())
                .cause(cause)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Create an empty builder for this exception.
     *
     * @return an empty builder.
     */
    public static DittoRuntimeExceptionBuilder<SubscriptionProtocolErrorException> newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code SubscriptionProtocolErrorException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubscriptionProtocolErrorException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubscriptionProtocolErrorException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link SubscriptionProtocolErrorException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubscriptionProtocolErrorException> {

        private Builder() {}

        @Override
        protected SubscriptionProtocolErrorException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new SubscriptionProtocolErrorException(dittoHeaders, message, description, cause, href);
        }
    }
}
