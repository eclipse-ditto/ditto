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
package org.eclipse.ditto.services.amqpbridge.messaging.persistence;

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;

/**
 * Data representing the state of a Connection which is used in order to persist snapshot state into MongoDB.
 */
public final class ConnectionData implements Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    private static final JsonFieldDefinition<JsonObject> AMQP_CONNECTION =
            JsonFactory.newJsonObjectFieldDefinition("amqpConnection", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<String> CONNECTION_STATUS =
            JsonFactory.newStringFieldDefinition("connectionStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);


    private final AmqpConnection amqpConnection;
    private final ConnectionStatus connectionStatus;

    /**
     * Constructs a new connection data instance.
     *
     * @param amqpConnection AmqpConnection information of the connection data.
     * @param connectionStatus ConnectionStatus information of the connection data.
     */
    public ConnectionData(final AmqpConnection amqpConnection, final ConnectionStatus connectionStatus) {

        this.amqpConnection = amqpConnection;
        this.connectionStatus = connectionStatus;
    }

    /**
     * @return the AmqpConnection information of the connection data.
     */
    public AmqpConnection getAmqpConnection() {
        return amqpConnection;
    }

    /**
     * @return the ConnectionStatus information of the connection data.
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    /**
     * Creates a new {@code ConnectionData} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the ConnectionData to be created.
     * @return a new ConnectionData which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    static ConnectionData fromJson(final JsonObject jsonObject) {

        final JsonObject readAmqpConnection = jsonObject.getValueOrThrow(AMQP_CONNECTION);
        final AmqpConnection amqpConnection = AmqpBridgeModelFactory.connectionFromJson(readAmqpConnection);

        final String readConnectionStatus = jsonObject.getValueOrThrow(CONNECTION_STATUS);
        final ConnectionStatus connectionStatus =
                ConnectionStatus.forName(readConnectionStatus).orElseThrow(() -> JsonParseException.newBuilder()
                        .message("Could not create ConnectionStatus from: " + jsonObject)
                        .build());

        return new ConnectionData(amqpConnection, connectionStatus);
    }

    @Override
    public JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(AMQP_CONNECTION, amqpConnection.toJson(schemaVersion, thePredicate), predicate);
        jsonObjectBuilder.set(CONNECTION_STATUS, connectionStatus.getName(), predicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectionData)) {
            return false;
        }
        final ConnectionData that = (ConnectionData) o;
        return Objects.equals(amqpConnection, that.amqpConnection) &&
                connectionStatus == that.connectionStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amqpConnection, connectionStatus);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "amqpConnection=" + amqpConnection +
                ", connectionStatus=" + connectionStatus +
                "]";
    }
}
