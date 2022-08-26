/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.model.ThingSearchException;

/**
 * Error response for search sessions that are aborted due to service restart.
 *
 * @since 3.0.0
 */
@JsonParsableException(errorCode = SubscriptionAbortedException.ERROR_CODE)
public class SubscriptionAbortedException extends DittoRuntimeException implements ThingSearchException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subscription.aborted";

    /**
     * Error message of this exception.
     */
    public static final String MESSAGE = "The subscription is aborted due to a service restart.";

    private static final HttpStatus STATUS_CODE = HttpStatus.INTERNAL_SERVER_ERROR;

    private SubscriptionAbortedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a {@code SubscriptionAbortedException}.
     *
     * @param dittoHeaders the Ditto headers.
     * @return the exception.
     */
    public static SubscriptionAbortedException of(final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(MESSAGE)
                .description("Please try again later.")
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code SubscriptionAbortedException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubscriptionAbortedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubscriptionAbortedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link SubscriptionAbortedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubscriptionAbortedException> {

        private Builder() {}

        @Override
        protected SubscriptionAbortedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new SubscriptionAbortedException(dittoHeaders, message, description, cause, href);
        }
    }

}
