/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.model;

import java.net.URI;

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
 * Thrown when a {@code TimeseriesQuery} is semantically invalid — for example a per-bucket
 * aggregation without a {@code step}, or a {@code percentile} aggregation with a missing or
 * out-of-range percentile value. Validation happens at the model layer so every transport
 * (HTTP, WebSocket, Connectivity) rejects the same inputs with HTTP {@code 400}.
 *
 * @since 4.0.0
 */
@Immutable
@JsonParsableException(errorCode = TimeseriesQueryInvalidException.ERROR_CODE)
public final class TimeseriesQueryInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "timeseries:query.invalid";

    private static final String DEFAULT_DESCRIPTION =
            "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).";

    private static final URI DEFAULT_HREF = URI.create("https://github.com/eclipse-ditto/ditto/issues/2291");

    private static final long serialVersionUID = -7034897190100176407L;

    private TimeseriesQueryInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code TimeseriesQueryInvalidException}.
     *
     * @param message the detail message describing what is invalid.
     * @return the builder.
     */
    public static Builder newBuilder(final String message) {
        return new Builder(message);
    }

    /**
     * Constructs a new {@code TimeseriesQueryInvalidException} from the given message.
     *
     * @param message the detail message.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static TimeseriesQueryInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code TimeseriesQueryInvalidException} from the message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the message field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TimeseriesQueryInvalidException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link TimeseriesQueryInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<TimeseriesQueryInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
            href(DEFAULT_HREF);
        }

        private Builder(final String message) {
            this();
            message(message);
        }

        @Override
        protected TimeseriesQueryInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new TimeseriesQueryInvalidException(dittoHeaders, message, description, cause, href);
        }
    }
}
