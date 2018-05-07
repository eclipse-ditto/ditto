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
package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

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
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponse;

/**
 * Response to a {@link RetrieveConnectionMetrics} command.
 */
@Immutable
public final class RetrieveConnectionMetricsResponse
        extends AbstractCommandResponse<RetrieveConnectionMetricsResponse>
        implements ConnectivityQueryCommandResponse<RetrieveConnectionMetricsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ConnectivityCommandResponse.TYPE_PREFIX + RetrieveConnectionMetrics.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION_METRICS =
            JsonFactory.newJsonObjectFieldDefinition("connectionMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String connectionId;
    private final ConnectionMetrics connectionMetrics;

    private RetrieveConnectionMetricsResponse(final String connectionId, final ConnectionMetrics connectionMetrics,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);

        this.connectionId = connectionId;
        this.connectionMetrics = connectionMetrics;
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionMetricsResponse}.
     *
     * @param connectionId the identifier of the connection.
     * @param connectionMetrics the retrieved connection metrics.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionMetricsResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionMetricsResponse of(final String connectionId,
            final ConnectionMetrics connectionMetrics, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(connectionMetrics, "Connection Status");

        return new RetrieveConnectionMetricsResponse(connectionId, connectionMetrics, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionMetricsResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be retrieved.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionMetricsResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionMetricsResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionMetricsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveConnectionMetricsResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final String readConnectionId =
                            jsonObject.getValueOrThrow(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID);
                    final ConnectionMetrics readConnectionMetrics = ConnectivityModelFactory.connectionMetricsFromJson(
                            jsonObject.getValueOrThrow(JSON_CONNECTION_METRICS));

                    return of(readConnectionId, readConnectionMetrics, dittoHeaders);
                });
    }

    /**
     * Returns the retrieved {@code ConnectionMetrics}.
     *
     * @return the ConnectionMetrics.
     */
    public ConnectionMetrics getConnectionMetrics() {
        return connectionMetrics;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
        jsonObjectBuilder.set(JSON_CONNECTION_METRICS, connectionMetrics.toJson(), predicate);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return connectionMetrics.toJson();
    }

    @Override
    public WithEntity setEntity(final JsonValue entity) {
        return fromJson(entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveConnectionMetricsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, connectionMetrics, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveConnectionMetricsResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        if (!super.equals(o)) {return false;}
        final RetrieveConnectionMetricsResponse that = (RetrieveConnectionMetricsResponse) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(connectionMetrics, that.connectionMetrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, connectionMetrics);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                ", connectionMetrics=" + connectionMetrics +
                "]";
    }

}
