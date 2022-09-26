/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Instant;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown if historical data of the Connection was either not present in Ditto at all or if the requester had insufficient
 * permissions to access it.
 *
 * @since 3.2.0
 */
@Immutable
@JsonParsableException(errorCode = ConnectionHistoryNotAccessibleException.ERROR_CODE)
public final class ConnectionHistoryNotAccessibleException extends DittoRuntimeException
        implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connection.history.notfound";

    private static final String MESSAGE_TEMPLATE =
            "The Connection with ID ''{0}'' at revision ''{1}'' could not be found or requester had insufficient " +
                    "permissions to access it.";

    private static final String MESSAGE_TEMPLATE_TS =
            "The Connection with ID ''{0}'' at timestamp ''{1}'' could not be found or requester had insufficient " +
                    "permissions to access it.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of your requested Connection was correct, you have sufficient permissions and ensure that the " +
                    "asked for revision/timestamp does not exceed the history-retention-duration.";

    private static final long serialVersionUID = -998877665544332221L;

    private ConnectionHistoryNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    private static String getMessage(final ConnectionId connectionId, final long revision) {
        checkNotNull(connectionId, "connectionId");
        return MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(connectionId), String.valueOf(revision));
    }

    private static String getMessage(final ConnectionId connectionId, final Instant timestamp) {
        checkNotNull(connectionId, "connectionId");
        checkNotNull(timestamp, "timestamp");
        return MessageFormat.format(MESSAGE_TEMPLATE_TS, String.valueOf(connectionId), timestamp.toString());
    }

    /**
     * A mutable builder for a {@code ConnectionHistoryNotAccessibleException}.
     *
     * @param connectionId the ID of the connection.
     * @param revision the asked for revision of the connection.
     * @return the builder.
     * @throws NullPointerException if {@code connectionId} is {@code null}.
     */
    public static Builder newBuilder(final ConnectionId connectionId, final long revision) {
        return new Builder(connectionId, revision);
    }

    /**
     * A mutable builder for a {@code ConnectionHistoryNotAccessibleException}.
     *
     * @param connectionId the ID of the connection.
     * @param timestamp the asked for timestamp of the connection.
     * @return the builder.
     * @throws NullPointerException if {@code connectionId} is {@code null}.
     */
    public static Builder newBuilder(final ConnectionId connectionId, final Instant timestamp) {
        return new Builder(connectionId, timestamp);
    }

    /**
     * Constructs a new {@code ConnectionHistoryNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionHistoryNotAccessibleException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ConnectionHistoryNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code ConnectionHistoryNotAccessibleException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionHistoryNotAccessibleException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionHistoryNotAccessibleException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link ConnectionHistoryNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionHistoryNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ConnectionId connectionId, final long revision) {
            this();
            message(ConnectionHistoryNotAccessibleException.getMessage(connectionId, revision));
        }

        private Builder(final ConnectionId connectionId, final Instant timestamp) {
            this();
            message(ConnectionHistoryNotAccessibleException.getMessage(connectionId, timestamp));
        }

        @Override
        protected ConnectionHistoryNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectionHistoryNotAccessibleException(dittoHeaders, message, description, cause, href);
        }

    }

}
