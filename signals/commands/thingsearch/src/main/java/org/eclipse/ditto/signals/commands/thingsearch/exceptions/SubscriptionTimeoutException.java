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
@JsonParsableException(errorCode = SubscriptionTimeoutException.ERROR_CODE)
public class SubscriptionTimeoutException extends DittoRuntimeException implements ThingSearchException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subscription.timeout";

    private static final HttpStatusCode STATUS_CODE = HttpStatusCode.REQUEST_TIMEOUT;

    private SubscriptionTimeoutException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a {@code SubscriptionTimeoutException}.
     *
     * @param subscriptionId ID of the nonexistent subscription.
     * @param dittoHeaders the Ditto headers.
     * @return the exception.
     */
    public static SubscriptionTimeoutException of(final String subscriptionId, final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(String.format("The subscription '%s' stopped due to a lack of interaction.", subscriptionId))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    protected DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> getEmptyBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code SubscriptionTimeoutException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubscriptionTimeoutException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubscriptionTimeoutException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    /**
     * A mutable builder with a fluent API for a {@link SubscriptionTimeoutException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubscriptionTimeoutException> {

        private Builder() {}

        @Override
        protected SubscriptionTimeoutException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new SubscriptionTimeoutException(dittoHeaders, message, description, cause, href);
        }
    }
}
