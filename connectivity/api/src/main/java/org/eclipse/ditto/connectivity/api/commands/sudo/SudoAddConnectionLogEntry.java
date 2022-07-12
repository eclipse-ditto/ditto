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
package org.eclipse.ditto.connectivity.api.commands.sudo;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.WithConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonRuntimeException;

/**
 * Transports a {@link LogEntry} that should be added to the log of a connection with a particular {@code ConnectionId}.
 *
 * @since 2.3.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivitySudoCommand.TYPE_PREFIX, name = SudoAddConnectionLogEntry.NAME)
public final class SudoAddConnectionLogEntry extends AbstractCommand<SudoAddConnectionLogEntry>
        implements ConnectivitySudoCommand<SudoAddConnectionLogEntry>, WithConnectionId {

    /**
     * Name of this command.
     */
    public static final String NAME = "sudoAddConnectionLogEntry";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final ConnectionId connectionId;
    private final LogEntry logEntry;

    private SudoAddConnectionLogEntry(final ConnectionId connectionId, final LogEntry logEntry,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.logEntry = checkNotNull(logEntry, "logEntry");
    }

    /**
     * Returns a new instance of {@code SudoAddConnectionLogEntry} for the specified {@code ConnectionId} and
     * {@code LogEntry} argument.
     *
     * @param connectionId ID of the connection to add the log entry for.
     * @param connectionLogEntry the entry to be added to the connection log.
     * @param dittoHeaders the headers of the request.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoAddConnectionLogEntry newInstance(final ConnectionId connectionId,
            final LogEntry connectionLogEntry, final DittoHeaders dittoHeaders) {

        return new SudoAddConnectionLogEntry(connectionId, connectionLogEntry, dittoHeaders);
    }

    /**
     * Deserializes an instance of {@code SudoAddConnectionLogEntry} from the specified {@code JsonObject} argument.
     *
     * @param jsonObject the JSON object to be deserialized.
     * @param dittoHeaders the headers of the command.
     * @return the deserialized {@code SudoAddConnectionLogEntry}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} does not contain all required
     * fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} cannot be deserialized because of
     * invalid data.
     */
    public static SudoAddConnectionLogEntry fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SudoAddConnectionLogEntry>(TYPE, jsonObject).deserialize(() -> {
            final String readConnectionId = jsonObject.getValueOrThrow(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID);
            final ConnectionId connectionId = ConnectionId.of(readConnectionId);
            final LogEntry logEntry = deserializeLogEntryOrThrow(jsonObject);

            return newInstance(connectionId, logEntry, dittoHeaders);
        });
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
    public Category getCategory() {
        return Category.ACTION;
    }

    @Override
    public SudoAddConnectionLogEntry setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(connectionId, logEntry, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, String.valueOf(connectionId),
                predicate);
        jsonObjectBuilder.set(JsonFields.LOG_ENTRY, logEntry.toJson(), predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof SudoAddConnectionLogEntry);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;}
        if (!super.equals(o)) {return false;
        }
        final SudoAddConnectionLogEntry that = (SudoAddConnectionLogEntry) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(logEntry, that.logEntry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, logEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                ", logEntry=" + logEntry +
                "]";
    }

    /**
     * Definitions of the fields an {@code SudoAddConnectionLogEntry} JSON object representation.
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
