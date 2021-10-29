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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonRuntimeException;

/**
 * Transports a {@link LogEntry} that should be added to connection log.
 *
 * @since 2.2.0
 */
@Immutable
public final class AddConnectionLogEntry implements Jsonifiable<JsonObject> {

    private final LogEntry logEntry;

    private AddConnectionLogEntry(final LogEntry logEntry) {
        this.logEntry = logEntry;
    }

    /**
     * Returns a new instance of {@code AddConnectionLogEntry} for the specified {@code LogEntry} argument.
     *
     * @param connectionLogEntry the entry to be added to the connection log.
     * @return the instance.
     * @throws NullPointerException if {@code connectionLogEntry} is {@code null}.
     */
    public static AddConnectionLogEntry of(final LogEntry connectionLogEntry) {
        return new AddConnectionLogEntry(checkNotNull(connectionLogEntry, "connectionLogEntry"));
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
        return new AddConnectionLogEntry(deserializeLogEntryOrThrow(checkNotNull(jsonObject, "jsonObject")));
    }

    private static LogEntry deserializeLogEntryOrThrow(final JsonObject jsonObject) {
        final JsonFieldDefinition<JsonObject> fieldDefinition = JsonFields.LOG_ENTRY;
        try {
            return ConnectivityModelFactory.logEntryFromJson(jsonObject.getValueOrThrow(fieldDefinition));
        } catch (final JsonRuntimeException e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize value of key <{0}> as {1}: {2}",
                            fieldDefinition.getPointer(),
                            LogEntry.class.getName(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }
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
        final AddConnectionLogEntry that = (AddConnectionLogEntry) o;
        return logEntry.equals(that.logEntry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "logEntry=" + logEntry +
                "]";
    }

    /**
     * Definitions of the fields an {@code AddConnectionLogEntry} JSON object representation.
     */
    @Immutable
    public static final class JsonFields {

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
