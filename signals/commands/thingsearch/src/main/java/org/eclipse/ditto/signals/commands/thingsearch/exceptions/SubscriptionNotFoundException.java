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
 * Error response for subscription commands addressing a nonexistent subscription.
 *
 * @since 1.1.0
 */
@JsonParsableException(errorCode = SubscriptionNotFoundException.ERROR_CODE)
public class SubscriptionNotFoundException extends DittoRuntimeException implements ThingSearchException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subscription.not.found";

    private static final HttpStatusCode STATUS_CODE = HttpStatusCode.NOT_FOUND;

    private SubscriptionNotFoundException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a {@code SubscriptionNotFoundException}.
     *
     * @param subscriptionId ID of the nonexistent subscription.
     * @param dittoHeaders the Ditto headers.
     * @return the exception.
     */
    public static SubscriptionNotFoundException of(final String subscriptionId, final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(String.format("No subscription with ID '%s' exists.", subscriptionId))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    protected DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> getEmptyBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code SubscriptionNotFoundException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubscriptionNotFoundException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static SubscriptionNotFoundException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(null))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.signals.commands.thingsearch.exceptions.SubscriptionNotFoundException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubscriptionNotFoundException> {

        private Builder() {}

        @Override
        protected SubscriptionNotFoundException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new SubscriptionNotFoundException(dittoHeaders, message, description, cause, href);
        }
    }
}
