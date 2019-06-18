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

import static org.eclipse.ditto.model.base.common.HttpStatusCode.REQUEST_HEADER_FIELDS_TOO_LARGE;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown when Ditto headers are too large to communicate within the cluster.
 */
@Immutable
@JsonParsableException(errorCode = DittoHeadersTooLargeException.ERROR_CODE)
public final class DittoHeadersTooLargeException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "headers.too.large";

    private static final String DEFAULT_MESSAGE = "The headers are too large.";

    private DittoHeadersTooLargeException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, REQUEST_HEADER_FIELDS_TOO_LARGE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code DittoHeadersTooLargeException} due to size limitation.
     *
     * @param maxSize maximum size of the headers.
     * @return the builder.
     */
    public static DittoRuntimeExceptionBuilder<DittoHeadersTooLargeException> newSizeLimitBuilder(final long maxSize) {
        final String msgPattern = "The number of bytes exceeded the maximum allowed value <{0}>!";
        return new Builder().description(MessageFormat.format(msgPattern, maxSize));
    }

    /**
     * A mutable builder for a {@code DittoHeadersTooLargeException} due to auth subjects limitation.
     *
     * @param actualAuthSubjectCount actual number of authorization subjects.
     * @param maxAuthSubjectsCount maximum number of authorization subjects.
     * @return the builder.
     */
    public static DittoRuntimeExceptionBuilder<DittoHeadersTooLargeException> newAuthSubjectsLimitBuilder(
            final int actualAuthSubjectCount, final int maxAuthSubjectsCount) {

        final String msgPtrn = "The number of authorization subjects <{0}> exceeded the maximum allowed value <{1}>.";
        return new Builder().description(MessageFormat.format(msgPtrn, actualAuthSubjectCount, maxAuthSubjectsCount));
    }

    /**
     * Constructs a new {@code DittoHeadersTooLargeException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new DittoHeadersTooLargeException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static DittoHeadersTooLargeException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(null))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link DittoHeadersTooLargeException}.
     */
    @NotThreadSafe
    private static final class Builder extends DittoRuntimeExceptionBuilder<DittoHeadersTooLargeException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
        }

        @Override
        protected DittoHeadersTooLargeException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new DittoHeadersTooLargeException(dittoHeaders, message, description, cause, href);
        }
    }
}

