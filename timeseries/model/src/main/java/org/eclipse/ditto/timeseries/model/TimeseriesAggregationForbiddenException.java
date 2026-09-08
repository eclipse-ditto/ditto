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
 * Thrown when a cross-Thing timeseries aggregation is not authorized for the requesting subject.
 * <p>
 * Unlike the single-Thing read path — which reports a denial as {@code 404 thing.notfound} so it
 * cannot be used to probe for the existence of a Thing — this is a plain {@code 403}. There is
 * nothing to conceal: the caller supplied the namespace themselves, so the response reveals no
 * information they did not already have.
 *
 * @since 4.0.0
 */
@Immutable
@JsonParsableException(errorCode = TimeseriesAggregationForbiddenException.ERROR_CODE)
public final class TimeseriesAggregationForbiddenException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "timeseries:aggregation.forbidden";

    private static final String DEFAULT_DESCRIPTION =
            "A cross-Thing aggregation requires a namespace-wide READ_TS grant, which is normally " +
                    "expressed through a namespace root policy. Per-Thing grants scattered across " +
                    "individual policies are not yet supported for cross-Thing queries; query those " +
                    "Things individually instead.";

    private static final URI DEFAULT_HREF = URI.create("https://github.com/eclipse-ditto/ditto/issues/2291");

    private static final long serialVersionUID = 4718225913077413355L;

    private TimeseriesAggregationForbiddenException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code TimeseriesAggregationForbiddenException}.
     *
     * @param message the detail message describing why the aggregation is not authorized.
     * @return the builder.
     */
    public static Builder newBuilder(final String message) {
        return new Builder(message);
    }

    /**
     * Returns the exception for a subject lacking a namespace-wide grant on the given namespace.
     *
     * @param namespace the namespace the aggregation targeted.
     * @param permission the permission that was required (e.g. {@code READ_TS}).
     * @return the builder, pre-filled with a message.
     */
    public static Builder forNamespace(final String namespace, final String permission) {
        return new Builder("Not authorized to aggregate timeseries across namespace <" + namespace +
                ">: no namespace-wide '" + permission + "' grant covers every requested path.");
    }

    /**
     * Constructs a new {@code TimeseriesAggregationForbiddenException} from the given message.
     *
     * @param message the detail message.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static TimeseriesAggregationForbiddenException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code TimeseriesAggregationForbiddenException} from the message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the message field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TimeseriesAggregationForbiddenException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link TimeseriesAggregationForbiddenException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<TimeseriesAggregationForbiddenException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
            href(DEFAULT_HREF);
        }

        private Builder(final String message) {
            this();
            message(message);
        }

        @Override
        protected TimeseriesAggregationForbiddenException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new TimeseriesAggregationForbiddenException(dittoHeaders, message, description,
                    cause, href);
        }
    }
}
