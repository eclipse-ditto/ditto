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
import org.eclipse.ditto.model.connectivity.ConnectivityException;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if a {@link Connection} exists but is not available at the moment.
 */
@Immutable
public final class ConnectionConflictException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connection.conflict";

    private static final String MESSAGE_TEMPLATE =
            "The Connection with ID ''{0}'' was already created.";

    private static final String DEFAULT_DESCRIPTION = "If you need to update it, remove it first before creating it again.";

    private static final long serialVersionUID = -4525302146860945435L;


    private ConnectionConflictException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectionConflictException}.
     *
     * @param connectionId the ID of the connection.
     * @return the builder.
     */
    public static Builder newBuilder(final String connectionId) {
        return new Builder(connectionId);
    }

    /**
     * Constructs a new {@code ConnectionConflictException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionConflictException.
     */
    public static ConnectionConflictException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ConnectionConflictException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionConflictException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ConnectionConflictException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link ConnectionConflictException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionConflictException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String connectionId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, connectionId));
        }

        @Override
        protected ConnectionConflictException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new ConnectionConflictException(dittoHeaders, message, description, cause, href);
        }
    }

}
