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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
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

    static final JsonFieldDefinition<JsonArray> JSON_MAPPING_CONTEXTS =
            JsonFactory.newJsonArrayFieldDefinition("mappingContexts", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Connection connection;
    private final List<MappingContext> mappingContexts;

    private ConnectionCreated(final Connection connection, final List<MappingContext> mappingContexts,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders) {
        super(TYPE, connection.getId(), timestamp, dittoHeaders);
        this.connection = connection;
        this.mappingContexts = Collections.unmodifiableList(new ArrayList<>(mappingContexts));
    }

    /**
     * Returns a new {@code ConnectionCreated} event.
     *
     * @param connection the created Connection.
     * @param mappingContexts the mapping contexts to apply for different content-types.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionCreated of(final Connection connection, final List<MappingContext> mappingContexts,
            final DittoHeaders dittoHeaders) {
        return of(connection, mappingContexts,null, dittoHeaders);
    }

    /**
     * Returns a new {@code ConnectionCreated} event.
     *
     * @param connection the created Connection.
     * @param mappingContexts the mapping contexts to apply for different content-types.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if {@code connection} or {@code dittoHeaders} are {@code null}.
     */
    public static ConnectionCreated of(final Connection connection, final List<MappingContext> mappingContexts,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders) {
        checkNotNull(connection, "Connection");
        checkNotNull(mappingContexts, "mapping Contexts");
        return new ConnectionCreated(connection, mappingContexts, timestamp, dittoHeaders);
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
                    final JsonArray mappingContexts = jsonObject.getValue(JSON_MAPPING_CONTEXTS)
                            .orElse(JsonFactory.newArray());
                    final List<MappingContext> readMappingContexts = mappingContexts.stream()
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(ConnectivityModelFactory::mappingContextFromJson)
                            .collect(Collectors.toList());

                    return of(readConnection, readMappingContexts, timestamp, dittoHeaders);
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
     * @return the configured {@link MappingContext}s of the created connection.
     */
    public List<MappingContext> getMappingContexts() {
        return mappingContexts;
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
        return of(connection, mappingContexts, getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONNECTION, connection.toJson(schemaVersion, thePredicate), predicate);
        jsonObjectBuilder.set(JSON_MAPPING_CONTEXTS, mappingContexts.stream()
                .map(ms -> ms.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate);
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
                Objects.equals(mappingContexts, that.mappingContexts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connection, mappingContexts);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connection=" + connection +
                ", mappingContexts=" + mappingContexts +
                "]";
    }
}
