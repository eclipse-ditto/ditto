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
 * Thrown if a {@link org.eclipse.ditto.connectivity.model.Connection} is not present.
 */
@Immutable
@JsonParsableException(errorCode = ConnectionNotAccessibleException.ERROR_CODE)
public final class ConnectionNotAccessibleException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connection.notfound";

    private static final String MESSAGE_TEMPLATE = "The Connection with ID ''{0}'' could not be found or " +
            "requester had insufficient permissions to access it.";

    private static final String DEFAULT_DESCRIPTION = "Check if the ID of your requested Connection was correct and" +
            " you have sufficient permissions.";

    private static final long serialVersionUID = -3207647419678933094L;

    private ConnectionNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectionNotAccessibleException}.
     *
     * @param connectionId the id of the connection.
     * @return the builder.
     */
    public static Builder newBuilder(final ConnectionId connectionId) {
        return new Builder(connectionId);
    }

    /**
     * Constructs a new {@code ConnectionNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE}
     * field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionNotAccessibleException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionNotAccessibleException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link ConnectionNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ConnectionId connectionId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(connectionId)));
        }

        @Override
        protected ConnectionNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectionNotAccessibleException(dittoHeaders, message, description, cause, href);
        }
    }

}
