/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if the the configuration of a {@link Connection} was invalid.
 */
@Immutable
public final class ConnectionConfigurationInvalidException extends DittoRuntimeException implements
        ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connection.configuration.invalid";

    private static final String DEFAULT_DESCRIPTION =
            "Make sure all required properties are set and valid in the configuration.";

    private static final long serialVersionUID = 8387870996010060745L;

    private ConnectionConfigurationInvalidException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectionConfigurationInvalidException}.
     *
     * @param errorMessage the error message.
     * @return the builder.
     */
    public static Builder newBuilder(final String errorMessage) {
        return new Builder(errorMessage);
    }

    /**
     * Constructs a new {@code ConnectionConfigurationInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionConfigurationInvalidException.
     */
    public static ConnectionConfigurationInvalidException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ConnectionConfigurationInvalidException} object with the exception message extracted from
     * the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionConfigurationInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ConnectionConfigurationInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link ConnectionConfigurationInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionConfigurationInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String errorMessage) {
            this();
            message(errorMessage);
        }

        @Override
        protected ConnectionConfigurationInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new ConnectionConfigurationInvalidException(dittoHeaders, message, description, cause, href);
        }
    }

}
