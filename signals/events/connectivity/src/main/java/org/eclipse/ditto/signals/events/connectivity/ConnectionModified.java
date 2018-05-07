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
package org.eclipse.ditto.signals.events.connectivity;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link Connection} was modified.
 */
@Immutable
public final class ConnectionModified extends AbstractConnectivityEvent<ConnectionModified>
        implements ConnectivityEvent<ConnectionModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "connectionModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Connection connection;

    private ConnectionModified(final Connection connection, @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {
        super(TYPE, connection.getId(), timestamp, dittoHeaders);
        this.connection = connection;
    }

    /**
     * Returns a new {@code ConnectionModified} event.
     *
     * @param connection the modified Connection.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionModified of(final Connection connection, final DittoHeaders dittoHeaders) {
        return of(connection,null, dittoHeaders);
    }

    /**
     * Returns a new {@code ConnectionModified} event.
     *
     * @param connection the modified Connection.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if {@code connection} or {@code dittoHeaders} are {@code null}.
     */
    public static ConnectionModified of(final Connection connection, @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {
        checkNotNull(connection, "Connection");
        return new ConnectionModified(connection, timestamp, dittoHeaders);
    }

    /**
     * Creates a {@code ConnectionModified} event from a JSON string.
     *
     * @param jsonString the JSON string of which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ConnectionModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a {@code ConnectionModified} event from a JSON object.
     *
     * @param jsonObject the JSON object of which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ConnectionModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final JsonObject connectionJsonObject = jsonObject.getValueOrThrow(JsonFields.CONNECTION);
                    final Connection readConnection = ConnectivityModelFactory.connectionFromJson(connectionJsonObject);

                    return of(readConnection, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the created {@code Connection}.
     *
     * @return the Connection.
     */
    public Connection getConnection() {
        return connection;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(connection.toJson(schemaVersion));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public ConnectionModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connection, getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.CONNECTION, connection.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ConnectionModified);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectionModified)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ConnectionModified that = (ConnectionModified) o;
        return Objects.equals(connection, that.connection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connection);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", connection=" + connection + "]";
    }
}
