/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
import org.eclipse.ditto.model.connectivity.ConnectivityException;

/**
 * Thrown if a connection command arrives while a connection operation is underway.
 */
@Immutable
public final class ConnectionBusyException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connection.busy";

    private static final String MESSAGE_TEMPLATE = "The Connection with ID ''{0}'' is {1}.";

    private static final String DESCRIPTION_TEMPLATE = "Please retry in {0} {1}.";

    private static final long serialVersionUID = 2648721759252899991L;


    private ConnectionBusyException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectionBusyException}.
     *
     * @param connectionId the ID of the connection.
     * @return the builder.
     */
    public static Builder newBuilder(final String connectionId) {
        return new Builder().connectionId(connectionId);
    }

    /**
     * Constructs a new {@code ConnectionBusyException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionBusyException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ConnectionBusyException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final Builder builder = new Builder();
        builder.message(readMessage(jsonObject));
        readDescription(jsonObject).ifPresent(builder::description);
        builder.dittoHeaders(dittoHeaders);
        return builder.build();
    }

    /**
     * A mutable builder with a fluent API for a {@link ConnectionBusyException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionBusyException> {

        private String connectionId = "UNKNOWN";

        private String operationName = "performing an unknown operation";
        private int timeout = 0;

        private Builder() {}

        /**
         * Set the connection ID.
         *
         * @param connectionId the connection ID.
         * @return this builder.
         */
        public Builder connectionId(final String connectionId) {
            this.connectionId = connectionId;
            setMessage();
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
            this.operationName = operationName;
            setMessage();
            return this;
        }

        /**
         * Set after how many seconds the user should attempt the command again.
         *
         * @param timeout timeout of the current connection operation in seconds.
         * @return this builder.
         */
        public Builder timeout(final int timeout) {
            this.timeout = timeout;
            setDescription();
            return this;
        }

        private void setMessage() {
            message(MessageFormat.format(MESSAGE_TEMPLATE, connectionId, operationName));
        }

        private void setDescription() {
            final String timeoutUnit = timeout == 1 ? "second" : "seconds";
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, timeout, timeoutUnit));
        }

        @Override
        protected ConnectionBusyException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new ConnectionBusyException(dittoHeaders, message, description, cause, href);
        }
    }

}

