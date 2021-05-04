/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 * Thrown for authorization errors on Connections.
 */
@Immutable
@JsonParsableException(errorCode = ConnectionUnauthorizedException.ERROR_CODE)
public final class ConnectionUnauthorizedException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connection.unauthorized";

    private static final String MESSAGE_TEMPLATE =
            "The Connection with ID <{0}> could not authenticate successfully. " +
                    "The underlying driver reported: <{1}>";

    private static final String DEFAULT_DESCRIPTION =
            "Please verify that the credentials were configured correctly in the connection.";

    private static final long serialVersionUID = -4525302146760945435L;

    private ConnectionUnauthorizedException(
            final DittoHeaders dittoHeaders,
            @Nullable final String message, @Nullable final String description,
            @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.UNAUTHORIZED, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code ConnectionUnauthorizedException}.
     *
     * @param connectionId the ConnectionId for which no authorization failed.
     * @param reason a detail reason why the authorization failed.
     * @return the new ConnectionUnauthorizedException.
     */
    public static ConnectionUnauthorizedException forConnectionId(final ConnectionId connectionId,
            final String reason) {
        return new Builder(connectionId, reason).build();
    }

    /**
     * Constructs a new {@code ConnectionUnauthorizedException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionUnauthorizedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionUnauthorizedException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link ConnectionUnauthorizedException}.
     */
    @NotThreadSafe
    static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionUnauthorizedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ConnectionId connectionId, final String reason) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(connectionId), reason));
        }

        @Override
        protected ConnectionUnauthorizedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectionUnauthorizedException(dittoHeaders, message, description, cause, href);
        }
    }
}
