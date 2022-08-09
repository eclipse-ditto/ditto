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
package org.eclipse.ditto.gateway.api;

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
 * This exception indicates that the websocket session is closed because the Authorization Context of the session
 * changed.
 */
@Immutable
@JsonParsableException(errorCode = GatewayWebsocketSessionClosedException.ERROR_CODE)
public final class GatewayWebsocketSessionClosedException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "websocket.session.closed";

    private static final String DEFAULT_MESSAGE =
            "The websocket session was closed because the authorization context changed.";

    private static final String DEFAULT_DESCRIPTION =
            "Changing the authorization context for an established websocket session isn't supported.";

    private static final String INVALID_TOKEN_MESSAGE = "The websocket session was closed because the JWT is invalid.";

    private static final long serialVersionUID = -1391574777788522077L;

    private GatewayWebsocketSessionClosedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayWebsocketSessionClosedException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code GatewayWebsocketSessionClosedException} thrown due to an invalid token.
     *
     * @return the builder.
     */
    public static Builder newBuilderForInvalidToken() {
        return new Builder(INVALID_TOKEN_MESSAGE, null);
    }

    /**
     * Constructs a new {@code GatewayWebsocketSessionClosedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayWebsocketSessionClosedException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static GatewayWebsocketSessionClosedException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code GatewayWebsocketSessionClosedException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayWebsocketSessionClosedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static GatewayWebsocketSessionClosedException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link GatewayWebsocketSessionClosedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayWebsocketSessionClosedException> {

        private Builder() {
            this(DEFAULT_MESSAGE, DEFAULT_DESCRIPTION);
        }

        private Builder(final String message, @Nullable final String description) {
            message(message);
            description(description);
        }

        @Override
        protected GatewayWebsocketSessionClosedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new GatewayWebsocketSessionClosedException(dittoHeaders, message, description, cause, href);
        }
    }
}
