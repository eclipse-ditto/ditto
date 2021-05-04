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
package org.eclipse.ditto.connectivity.model.signals.commands.exceptions;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Duration;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityException;

/**
 * Thrown if a connection command arrives while a connection operation is underway.
 */
@Immutable
@JsonParsableException(errorCode = ConnectionSignalIllegalException.ERROR_CODE)
public final class ConnectionSignalIllegalException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "signal.illegal";

    private static final String OPERATING_MESSAGE_TEMPLATE = "The Connection with ID ''{0}'' is {1}.";

    private static final String OPERATING_DESCRIPTION_TEMPLATE = "Please retry in {0} {1}.";

    private static final String DEFAULT_DESCRIPTION = "Please retry later.";

    private static final String STAYING_MESSAGE_TEMPLATE = "The message ''{2}'' is illegal for the {1} Connection " +
            "with ID ''{0}''";

    private static final long serialVersionUID = 2648721759252899991L;


    private ConnectionSignalIllegalException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectionBusyException}.
     *
     * @param connectionId the ID of the connection.
     * @return the builder.
     */
    public static Builder newBuilder(final ConnectionId connectionId) {
        return new Builder().connectionId(connectionId);
    }

    /**
     * Constructs a new {@code ConnectionBusyException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionBusyException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionSignalIllegalException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link ConnectionSignalIllegalException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionSignalIllegalException> {

        private ConnectionId connectionId = ConnectionId.of("UNKNOWN");

        private Builder() {
            this.description(DEFAULT_DESCRIPTION);
        }

        /**
         * Set the connection ID.
         *
         * @param connectionId the connection ID.
         * @return this builder.
         */
        public Builder connectionId(final ConnectionId connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        /**
         * Set what is happening. The operation name is the gerund complementing "is" in the error message;
         * good choices are lower-case verbs in ING-form such as "connecting" and "disconnecting".
         *
         * @param operationName name of the operation.
         * @return this builder.
         */
        public Builder operationName(final String operationName) {
            message(MessageFormat.format(OPERATING_MESSAGE_TEMPLATE, String.valueOf(connectionId), operationName));
            return this;
        }

        /**
         * Set description to after how many seconds the user should attempt the command again.
         *
         * @param timeoutInSeconds timeout of the current connection operation in seconds.
         * @return this builder.
         */
        public Builder timeout(final long timeoutInSeconds) {
            final String timeoutUnit = timeoutInSeconds == 1 ? "second" : "seconds";
            description(MessageFormat.format(OPERATING_DESCRIPTION_TEMPLATE, timeoutInSeconds, timeoutUnit));
            return this;
        }

        /**
         * Set description to after how many seconds the user should attempt the command again.
         *
         * @param timeout timeout of the current connection operation in seconds.
         * @return this builder.
         */
        public Builder timeout(final Duration timeout) {
            return timeout(timeout.getSeconds());
        }


        /**
         * Set message to about a signal arriving when the connection state does not handle it.
         *
         * @param signalType type of the signal.
         * @param state state of the connection. Should be an adjective.
         * @return this builder.
         */
        public Builder illegalSignalForState(final String signalType, final String state) {
            message(MessageFormat.format(STAYING_MESSAGE_TEMPLATE, connectionId, state, signalType));
            return this;
        }

        @Override
        protected ConnectionSignalIllegalException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectionSignalIllegalException(dittoHeaders, message, description, cause, href);
        }
    }

}

