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
package org.eclipse.ditto.connectivity.model.signals.commands.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.connectivity.model.ConnectivityException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown if a {@link org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnections} contains too
 * many connection-ids.
 */
@Immutable
@JsonParsableException(errorCode = ConnectionsAmountIllegalException.ERROR_CODE)
public final class ConnectionsAmountIllegalException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connections.amount.illegal";

    private static final String MESSAGE_TEMPLATE =
            "The amount of requested exceptions exceeds the limit of ''{0}''.";

    private static final String DEFAULT_DESCRIPTION = "Please request less connection ids.";

    private static final long serialVersionUID = -4525302100000945435L;


    private ConnectionsAmountIllegalException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectionsAmountIllegalException}.
     *
     * @param maxConnectionsAmount the maximum amount of connection-ids.
     * @return the builder.
     */
    public static Builder newBuilder(final int maxConnectionsAmount) {
        return new Builder(maxConnectionsAmount);
    }

    /**
     * Constructs a new {@code ConnectionsAmountIllegalException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionConflictException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ConnectionsAmountIllegalException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code ConnectionsAmountIllegalException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionConflictException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionsAmountIllegalException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link ConnectionsAmountIllegalException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionsAmountIllegalException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final int maxConnectionsAmount) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(maxConnectionsAmount)));
        }

        @Override
        protected ConnectionsAmountIllegalException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectionsAmountIllegalException(dittoHeaders, message, description, cause, href);
        }
    }

}
