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
package org.eclipse.ditto.signals.commands.amqpbridge.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.amqpbridge.MappingScript;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;

/**
 * Command which creates a {@link AmqpConnection}.
 */
@Immutable
public final class CreateConnection extends AbstractCommand<CreateConnection>
        implements AmqpBridgeModifyCommand<CreateConnection> {

    /**
     * Name of this command.
     */
    public static final String NAME = "createConnection";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION =
            JsonFactory.newJsonObjectFieldDefinition("connection", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_MAPPING_SCRIPTS =
            JsonFactory.newJsonArrayFieldDefinition("mappingScripts", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final AmqpConnection amqpConnection;
    private final List<MappingScript> mappingScripts;

    private CreateConnection(final AmqpConnection amqpConnection, final List<MappingScript> mappingScripts,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.amqpConnection = amqpConnection;
        this.mappingScripts = Collections.unmodifiableList(new ArrayList<>(mappingScripts));
    }

    /**
     * Returns a new instance of {@code CreateConnection}.
     *
     * @param amqpConnection the connection to be created.
     * @param dittoHeaders the headers of the request.
     * @return a new CreateConnection command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateConnection of(final AmqpConnection amqpConnection, final DittoHeaders dittoHeaders) {
        return of(amqpConnection, Collections.emptyList(), dittoHeaders);
    }

    /**
     * Returns a new instance of {@code CreateConnection}.
     *
     * @param amqpConnection the connection to be created.
     * @param mappingScripts the mapping scripts to apply for different content-types.
     * @param dittoHeaders the headers of the request.
     * @return a new CreateConnection command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateConnection of(final AmqpConnection amqpConnection, final List<MappingScript> mappingScripts,
            final DittoHeaders dittoHeaders) {
        checkNotNull(amqpConnection, "Connection");
        checkNotNull(mappingScripts, "mapping Scripts");
        return new CreateConnection(amqpConnection, mappingScripts, dittoHeaders);
    }

    /**
     * Creates a new {@code CreateConnection} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CreateConnection fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code CreateConnection} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CreateConnection fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CreateConnection>(TYPE, jsonObject).deserialize(() -> {
            final JsonObject jsonConnection = jsonObject.getValueOrThrow(JSON_CONNECTION);
            final AmqpConnection readAmqpConnection = AmqpBridgeModelFactory.connectionFromJson(jsonConnection);
            final JsonArray mappingScripts = jsonObject.getValueOrThrow(JSON_MAPPING_SCRIPTS);
            final List<MappingScript> readMappingScripts = mappingScripts.stream()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(AmqpBridgeModelFactory::mappingScriptFromJson)
                    .collect(Collectors.toList());

            return of(readAmqpConnection, readMappingScripts, dittoHeaders);
        });
    }

    /**
     * @return the {@code AmqpConnection} to be created.
     */
    public AmqpConnection getAmqpConnection() {
        return amqpConnection;
    }

    /**
     * @return
     */
    public List<MappingScript> getMappingScripts() {
        return mappingScripts;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONNECTION, amqpConnection.toJson(schemaVersion, thePredicate), predicate);
        jsonObjectBuilder.set(JSON_MAPPING_SCRIPTS, mappingScripts.stream()
                        .map(ms -> ms.toJson(schemaVersion, thePredicate))
                        .collect(JsonCollectors.valuesToArray()), predicate);
    }

    @Override
    public String getConnectionId() {
        return amqpConnection.getId();
    }

    @Override
    public CreateConnection setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(amqpConnection, mappingScripts, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof CreateConnection);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final CreateConnection that = (CreateConnection) o;
        return Objects.equals(amqpConnection, that.amqpConnection) && Objects.equals(mappingScripts, that.mappingScripts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), amqpConnection, mappingScripts);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", amqpConnection=" + amqpConnection +
                ", mappingScripts=" + mappingScripts +
                "]";
    }

}
