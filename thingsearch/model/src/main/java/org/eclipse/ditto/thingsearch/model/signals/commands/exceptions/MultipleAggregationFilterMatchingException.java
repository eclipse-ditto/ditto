/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.model.ThingSearchException;

/**
 * Thrown if during a custom aggregation metrics gathering multiple "filters" matched at the same time whereas only
 * one filter is allowed to match.
 *
 * @since 3.6.2
 */
@Immutable
@JsonParsableException(errorCode = MultipleAggregationFilterMatchingException.ERROR_CODE)
public class MultipleAggregationFilterMatchingException extends DittoRuntimeException implements ThingSearchException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "multiple.aggregation.filter.matching";

    static final String DEFAULT_DESCRIPTION = "Ensure that only one defined 'filter' can match at the same time.";

    static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    private static final long serialVersionUID = -6341839112047194476L;

    private MultipleAggregationFilterMatchingException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HTTP_STATUS, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code MultipleAggregationFilterMatchingException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code MultipleAggregationFilterMatchingException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MultipleAggregationFilterMatchingException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static MultipleAggregationFilterMatchingException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.MultipleAggregationFilterMatchingException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<MultipleAggregationFilterMatchingException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected MultipleAggregationFilterMatchingException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new MultipleAggregationFilterMatchingException(dittoHeaders, message, description, cause, href);
        }

    }

}
