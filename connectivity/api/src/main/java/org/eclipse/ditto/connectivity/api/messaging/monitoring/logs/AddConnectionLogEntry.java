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
package org.eclipse.ditto.connectivity.api.messaging.monitoring.logs;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionIdInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.WithConnectionId;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonRuntimeException;

/**
 * Transports a {@link LogEntry} that should be added to the log of a connection with a particular {@code ConnectionId}.
 *
 * @since 2.3.0
 */
@Immutable
public final class AddConnectionLogEntry implements Jsonifiable<JsonObject>, WithConnectionId {

    private final ConnectionId connectionId;
    private final LogEntry logEntry;

    private AddConnectionLogEntry(final ConnectionId connectionId, final LogEntry logEntry) {
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.logEntry = checkNotNull(logEntry, "logEntry");
    }

    /**
     * Returns a new instance of {@code AddConnectionLogEntry} for the specified {@code ConnectionId} and
     * {@code LogEntry} argument.
     *
     * @param connectionId ID of the connection to add the log entry for.
     * @param connectionLogEntry the entry to be added to the connection log.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AddConnectionLogEntry newInstance(final ConnectionId connectionId,
            final LogEntry connectionLogEntry) {

        return new AddConnectionLogEntry(connectionId, connectionLogEntry);
    }

    /**
     * Deserializes an instance of {@code AddConnectionLogEntry} from the specified {@code JsonObject} argument.
     *
     * @param jsonObject the JSON object to be deserialized.
     * @return the deserialized {@code AddConnectionLogEntry}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} does not contain all required
     * fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} cannot be deserialized because of
     * invalid data.
     */
    public static AddConnectionLogEntry fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        return new AddConnectionLogEntry(deserializeConnectionIdOrThrow(jsonObject),
                deserializeLogEntryOrThrow(jsonObject));
    }

    private static ConnectionId deserializeConnectionIdOrThrow(final JsonObject jsonObject) {
        final var fieldDefinition = JsonFields.CONNECTION_ID;
        try {
            return ConnectionId.of(jsonObject.getValueOrThrow(fieldDefinition));
        } catch (final JsonRuntimeException | ConnectionIdInvalidException e) {
            throw newJsonParseException(fieldDefinition, ConnectionId.class, e);
        }
    }

    private static JsonParseException newJsonParseException(final JsonFieldDefinition<?> fieldDefinition,
            final Class<?> targetClass,
            final Exception cause) {

        return JsonParseException.newBuilder()
                .message(MessageFormat.format("Failed to deserialize value of key <{0}> as {1}: {2}",
                        fieldDefinition.getPointer(),
                        targetClass.getName(),
                        cause.getMessage()))
                .cause(cause)
                .build();
    }

    private static LogEntry deserializeLogEntryOrThrow(final JsonObject jsonObject) {
        final var fieldDefinition = JsonFields.LOG_ENTRY;
        try {
            return ConnectivityModelFactory.logEntryFromJson(jsonObject.getValueOrThrow(fieldDefinition));
        } catch (final JsonRuntimeException e) {
            throw newJsonParseException(fieldDefinition, LogEntry.class, e);
        }
    }

    @Override
    public ConnectionId getEntityId() {
        return connectionId;
    }

    /**
     * Returns the entry to be added to the connection log.
     *
     * @return the connection log entry.
     */
    public LogEntry getLogEntry() {
        return logEntry;
    }

    @Override
    public JsonObject toJson() {
        return JsonObject.newBuilder()
                .set(JsonFields.CONNECTION_ID, connectionId.toString())
                .set(JsonFields.LOG_ENTRY, logEntry.toJson())
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (AddConnectionLogEntry) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(logEntry, that.logEntry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, logEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", logEntry=" + logEntry +
                "]";
    }

    /**
     * Definitions of the fields an {@code AddConnectionLogEntry} JSON object representation.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * Definition of a field containing the String representation of the {@link ConnectionId}.
         */
        public static final JsonFieldDefinition<String> CONNECTION_ID =
                JsonFieldDefinition.ofString("connectionId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Definition of a field containing the JSON object representation of the {@link LogEntry}.
         */
        public static final JsonFieldDefinition<JsonObject> LOG_ENTRY =
                JsonFieldDefinition.ofJsonObject("logEntry", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
