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
package org.eclipse.ditto.signals.commands.amqpbridge.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.amqpbridge.AmqpBridgeCommandResponse;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.base.WithEntity;

import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;

/**
 * Response to a {@link RetrieveConnectionStatuses} command.
 */
@Immutable
public final class RetrieveConnectionStatusesResponse
        extends AbstractCommandResponse<RetrieveConnectionStatusesResponse>
        implements AmqpBridgeQueryCommandResponse<RetrieveConnectionStatusesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = AmqpBridgeCommandResponse.TYPE_PREFIX + RetrieveConnectionStatuses.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION_STATUSES =
            JsonFactory.newJsonObjectFieldDefinition("connectionStatuses", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Map<String, ConnectionStatus> connectionStatuses;

    private RetrieveConnectionStatusesResponse(final Map<String, ConnectionStatus> connectionStatuses,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);

        this.connectionStatuses = Collections.unmodifiableMap(new HashMap<>(connectionStatuses));
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionStatusesResponse}.
     *
     * @param connectionStatuses the retrieved connection statuses.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionStatusesResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionStatusesResponse of(final Map<String, ConnectionStatus> connectionStatuses,
            final DittoHeaders dittoHeaders) {
        checkNotNull(connectionStatuses, "Connection Statuses");

        return new RetrieveConnectionStatusesResponse(connectionStatuses, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionStatusesResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be retrieved.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionStatusesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionStatusesResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionStatusesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveConnectionStatusesResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final JsonObject connectionStatusesJson = jsonObject.getValueOrThrow(JSON_CONNECTION_STATUSES);
                    final Map<String, ConnectionStatus> readConnectionStatuses =
                            connectionStatusesJson.stream().collect(Collectors.toMap(
                                    jf -> jf.getKey().toString(),
                                    jf -> ConnectionStatus.forName(jf.getValue().asString())
                                            .orElse(ConnectionStatus.UNKNOWN)));

                    return of(readConnectionStatuses, dittoHeaders);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObject connectionStatusesJson = this.connectionStatuses.entrySet().stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), JsonValue.of(entry.getValue().getName())))
                .collect(JsonCollectors.fieldsToObject());

        jsonObjectBuilder.set(JSON_CONNECTION_STATUSES, connectionStatusesJson, predicate);
    }

    /**
     * This response does not have an ID. Thus this implementation always returns an empty string.
     *
     * @return an empty string.
     */
    @Override
    public String getConnectionId() {
        return "";
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return connectionStatuses.entrySet().stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), JsonValue.of(entry.getValue().getName())))
                .collect(JsonCollectors.fieldsToObject());
    }

    @Override
    public WithEntity setEntity(final JsonValue entity) {
        return fromJson(entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveConnectionStatusesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionStatuses, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        if (!super.equals(o)) {return false;}
        final RetrieveConnectionStatusesResponse that = (RetrieveConnectionStatusesResponse) o;
        return Objects.equals(connectionStatuses, that.connectionStatuses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionStatuses);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionStatuses=" + connectionStatuses +
                "]";
    }

}
