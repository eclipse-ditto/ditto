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
package org.eclipse.ditto.base.model.acks;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * Thrown if any actor involved in the pubsub infrastructure terminated abnormally, losing information about local
 * subscribers.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableException(errorCode = PubSubTerminatedException.ERROR_CODE)
public final class PubSubTerminatedException extends DittoRuntimeException implements FatalPubSubException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "pubsub:abnormal.termination";

    private static final String DEFAULT_MESSAGE = "The signal pubsub infrastructure terminated abnormally.";

    private static final String DEFAULT_DESCRIPTION = "Please try again later.";

    private static final PubSubTerminatedException INSTANCE = PubSubTerminatedException.newBuilder().build();

    private PubSubTerminatedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders, message, description, cause, href);
    }

    /**
     * Get a static instance of this exception with empty Ditto headers.
     *
     * @return the instance.
     */
    public static PubSubTerminatedException getInstance() {
        return INSTANCE;
    }

    /**
     * A mutable builder for this exception.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Deserialize from a JSON object.
     *
     * @param jsonObject the JSON object to deserialize.
     * @param dittoHeaders the headers.
     * @return an instance of this class.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} is missing required JSON fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contains unexpected value types.
     */
    public static PubSubTerminatedException fromJson(final JsonObject jsonObject,
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

    @Override
    public DittoRuntimeException asDittoRuntimeException() {
        return this;
    }

    /**
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.acks.PubSubTerminatedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PubSubTerminatedException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected PubSubTerminatedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PubSubTerminatedException(dittoHeaders, message, description, cause, href);
        }

    }
}
