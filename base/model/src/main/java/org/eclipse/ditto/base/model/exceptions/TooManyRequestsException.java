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
package org.eclipse.ditto.base.model.exceptions;

import java.net.URI;
import java.time.Duration;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown if too many requests were done (e.g. via a WebSocket connection) in a short amount of time.
 */
@Immutable
@JsonParsableException(errorCode = TooManyRequestsException.ERROR_CODE)
public final class TooManyRequestsException extends DittoRuntimeException implements GeneralException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "too.many.requests";

    /**
     * Retry-After header specified by RFC-6585 SS.4.
     */
    static final String RETRY_AFTER = "retry-after";

    private static final HttpStatus STATUS_CODE = HttpStatus.TOO_MANY_REQUESTS;
    private static final String MESSAGE = "You made too many requests.";
    private static final String DESCRIPTION = "Try again soon.";

    private TooManyRequestsException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
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
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static TooManyRequestsException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {

        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code TooManyRequestsException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new TooManyRequestsException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static TooManyRequestsException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link TooManyRequestsException}.
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
