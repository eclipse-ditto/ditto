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
package org.eclipse.ditto.signals.commands.thingsearch.exceptions;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.thingsearch.ThingSearchException;

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

    private static final HttpStatusCode STATUS_CODE = HttpStatusCode.BAD_REQUEST;

    private SubscriptionProtocolErrorException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
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

    @Override
    protected DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> getEmptyBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code SubscriptionProtocolErrorException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubscriptionProtocolErrorException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static SubscriptionProtocolErrorException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(null))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.signals.commands.thingsearch.exceptions.SubscriptionProtocolErrorException}.
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
