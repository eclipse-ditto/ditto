/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.connectivity.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityException;

/**
 * Thrown for timeout errors on operations on Connections.
 */
@Immutable
@JsonParsableException(errorCode = ConnectionTimeoutException.ERROR_CODE)
public class ConnectionTimeoutException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connection.timeout";

    private static final String MESSAGE_TEMPLATE = "The operation ''{0}'' on connection ''{1}'' timed out.";

    private static final String DEFAULT_DESCRIPTION = "The Connection did not answer within timeout.";

    private static final long serialVersionUID = -7796330038424432186L;

    private ConnectionTimeoutException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.SERVICE_UNAVAILABLE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectionTimeoutException}.
     *
     * @param connectionId the ID of the connection.
     * @return the builder.
     */
    public static ConnectionTimeoutException.Builder newBuilder(final ConnectionId connectionId,
            final String operation) {
        return new ConnectionTimeoutException.Builder(connectionId, operation);
    }

    /**
     * Constructs a new {@code ConnectionTimeoutException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionTimeoutException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ConnectionTimeoutException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code ConnectionTimeoutException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionTimeoutException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionTimeoutException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    /**
     * A mutable builder with a fluent API for a {@link ConnectionTimeoutException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionTimeoutException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ConnectionId connectionId, final String operation) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, operation, String.valueOf(connectionId)));
        }

        @Override
        protected ConnectionTimeoutException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectionTimeoutException(dittoHeaders, message, description, cause, href);
        }
    }
}
