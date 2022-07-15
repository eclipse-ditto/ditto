/*
 * Copyright Bosch.IO GmbH 2021
 *
 *  All rights reserved, also regarding any disposal, exploitation,
 *  reproduction, editing, distribution, as well as in the event of
 *  applications for industrial property rights.
 *
 *  This software is the confidential and proprietary information
 *  of Bosch.IO GmbH. You shall not disclose
 *  such Confidential Information and shall use it only in
 *  accordance with the terms of the license agreement you
 *  entered into with Bosch.IO GmbH.
 */
package org.eclipse.ditto.connectivity.model.signals.commands.exceptions;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.connectivity.model.ConnectivityException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown if an invalid command is sent to a connection.
 */
@Immutable
@JsonParsableException(errorCode = ConnectivityCommandInvalidException.ERROR_CODE)
public final class ConnectivityCommandInvalidException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ConnectivityException.ERROR_CODE_PREFIX + "command.invalid";

    private static final String MESSAGE_PATTERN = "The command <%s> is invalid or unknown.";

    private static final String DEFAULT_DESCRIPTION =
            "Choose from one of the valid commands, e.g. <" + OpenConnection.TYPE + ">.";

    private ConnectivityCommandInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectionIdNotExplicitlySettableException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder(final String invalidCommand) {
        return new Builder(invalidCommand);
    }

    /**
     * Constructs a new {@code ConnectivityCommandInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectivityCommandInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectivityCommandInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder(""));
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder("")
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link ConnectionIdNotExplicitlySettableException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectivityCommandInvalidException> {

        private Builder(final String command) {
            message(String.format(MESSAGE_PATTERN, command));
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected ConnectivityCommandInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectivityCommandInvalidException(dittoHeaders, message, description, cause, href);
        }
    }
}
