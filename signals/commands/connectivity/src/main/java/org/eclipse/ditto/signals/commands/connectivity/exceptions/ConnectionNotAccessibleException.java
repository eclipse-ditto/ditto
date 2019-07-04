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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityException;

/**
 * Thrown if a {@link Connection} is not present.
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

    private final String connectionId;

    private ConnectionNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href,
            final String connectionId) {
        super(ERROR_CODE, HttpStatusCode.NOT_FOUND, dittoHeaders, message, description, cause, href);
        this.connectionId = connectionId;
    }

    /**
     * A mutable builder for a {@code ConnectionNotAccessibleException}.
     *
     * @param id the id of the connection.
     * @return the builder.
     */
    public static Builder newBuilder(final String id) {
        return new Builder(id);
    }

    /**
     * Constructs a new {@code ConnectionNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE}
     * field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectionNotAccessibleException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static ConnectionNotAccessibleException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder(readConnectionId(jsonObject))
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    private static String readConnectionId(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        return jsonObject.getValueOrThrow(JsonFields.CONNECTION_ID);
    }

    public String getConnectionId() {
        return connectionId;
    }

    @Override
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        super.appendToJson(jsonObjectBuilder, predicate);
        jsonObjectBuilder.set(JsonFields.CONNECTION_ID, connectionId);
    }

    /**
     * A mutable builder with a fluent API for a {@link ConnectionNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectionNotAccessibleException> {

        private final String connectionId;

        private Builder(final String id) {
            this.connectionId = id;
            message(MessageFormat.format(MESSAGE_TEMPLATE, id));
        }

        @Override
        protected ConnectionNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectionNotAccessibleException(dittoHeaders, message, description, cause, href, connectionId);
        }
    }


    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a {@code DittoRuntimeException}.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing connection id of the connection that is not accessible.
         */
        public static final JsonFieldDefinition<String> CONNECTION_ID =
                JsonFactory.newStringFieldDefinition("connectionId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
