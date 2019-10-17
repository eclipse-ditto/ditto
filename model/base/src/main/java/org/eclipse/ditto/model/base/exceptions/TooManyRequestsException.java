/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.exceptions;

import java.net.URI;
import java.time.Duration;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown if too many requests were done (e.g. via a WebSocket connection) in a short amount of time.
 */
@JsonParsableException(errorCode = TooManyRequestsException.ERROR_CODE)
public final class TooManyRequestsException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "too.many.requests";

    /**
     * Retry-After header specified by RFC-6585 SS.4.
     */
    static final String RETRY_AFTER = "retry-after";

    private static final HttpStatusCode STATUS_CODE = HttpStatusCode.TOO_MANY_REQUESTS;
    private static final String MESSAGE = "You made too many requests.";
    private static final String DESCRIPTION = "Try again soon.";

    private TooManyRequestsException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<TooManyRequestsException> getEmptyBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code TooManyRequestsException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code TooManyRequestsException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new TooManyRequestsException.
     */
    public static TooManyRequestsException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code TooManyRequestsException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new TooManyRequestsException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static TooManyRequestsException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(null))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.model.base.exceptions.TooManyRequestsException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<TooManyRequestsException> {

        @Nullable
        private Duration retryAfter;

        private Builder() {
            message(MESSAGE);
            description(DESCRIPTION);
        }

        /**
         * Set "retry-after" header. Accurate to seconds in accord with RFC-6585.
         *
         * @param duration retry after how long.
         * @return this builder.
         */
        public Builder retryAfter(final Duration duration) {
            retryAfter = duration;
            return this;
        }

        @Override
        protected TooManyRequestsException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            final DittoHeaders headersWithRetryAfter;
            if (retryAfter == null) {
                headersWithRetryAfter = dittoHeaders;
            } else {
                headersWithRetryAfter = dittoHeaders.toBuilder()
                        .putHeader(RETRY_AFTER, String.valueOf(retryAfter.getSeconds()))
                        .build();
            }
            return new TooManyRequestsException(headersWithRetryAfter, message, description, cause, href);
        }

    }

}
