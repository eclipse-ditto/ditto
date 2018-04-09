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
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link Connection} was created.
 */
@Immutable
public final class ConnectionCreated extends AbstractConnectivityEvent<ConnectionCreated>
        implements ConnectivityEvent<ConnectionCreated> {

    /**
     * Name of this event.
     */
    public static final String NAME = "connectionCreated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION =
            JsonFactory.newJsonObjectFieldDefinition("connection", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_MAPPING_CONTEXT =
            JsonFactory.newJsonObjectFieldDefinition("mappingContext", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Connection connection;
    @Nullable private final MappingContext mappingContext;

    private ConnectionCreated(final Connection connection, @Nullable final MappingContext mappingContext,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders) {
        super(TYPE, connection.getId(), timestamp, dittoHeaders);
        this.connection = connection;
        this.mappingContext = mappingContext;
    }

    /**
     * Returns a new {@code ConnectionCreated} event.
     *
     * @param connection the created Connection.
     * @param mappingContext the mapping context to apply.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionCreated of(final Connection connection, @Nullable final MappingContext mappingContext,
            final DittoHeaders dittoHeaders) {
        return of(connection, mappingContext,null, dittoHeaders);
    }

    /**
     * Returns a new {@code ConnectionCreated} event.
     *
     * @param connection the created Connection.
     * @param mappingContext the mapping context to apply.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if {@code connection} or {@code dittoHeaders} are {@code null}.
     */
    public static ConnectionCreated of(final Connection connection, @Nullable final MappingContext mappingContext,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders) {
        checkNotNull(connection, "Connection");
        return new ConnectionCreated(connection, mappingContext, timestamp, dittoHeaders);
    }

    /**
     * Creates a {@code ConnectionCreated} event from a JSON string.
     *
     * @param jsonString the JSON string of which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ConnectionCreated fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a {@code ConnectionCreated} event from a JSON object.
     *
     * @param jsonObject the JSON object of which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ConnectionCreated>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final JsonObject connectionJsonObject = jsonObject.getValueOrThrow(JSON_CONNECTION);
                    final Connection readConnection = ConnectivityModelFactory.connectionFromJson(connectionJsonObject);

                    final JsonObject readMappingContext = jsonObject.getValue(JSON_MAPPING_CONTEXT).orElse(null);
                    final MappingContext mappingContext = readMappingContext != null ?
                            ConnectivityModelFactory.mappingContextFromJson(readMappingContext) : null;

                    return of(readConnection, mappingContext, timestamp, dittoHeaders);
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

    /**
     * @return the configured {@link MappingContext} of the created connection.
     */
    public Optional<MappingContext> getMappingContext() {
        return Optional.ofNullable(mappingContext);
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
    public ConnectionCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connection, mappingContext, getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONNECTION, connection.toJson(schemaVersion, thePredicate), predicate);
        if (mappingContext != null) {
            jsonObjectBuilder.set(JSON_MAPPING_CONTEXT, mappingContext.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ConnectionCreated);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectionCreated)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ConnectionCreated that = (ConnectionCreated) o;
        return Objects.equals(connection, that.connection) &&
                Objects.equals(mappingContext, that.mappingContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connection, mappingContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connection=" + connection +
                ", mappingContext=" + mappingContext +
                "]";
    }
}
