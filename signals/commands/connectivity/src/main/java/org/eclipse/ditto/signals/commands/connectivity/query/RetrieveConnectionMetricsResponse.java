/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponse;

/**
 * Response to a {@link RetrieveConnectionMetrics} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveConnectionMetricsResponse.TYPE)
public final class RetrieveConnectionMetricsResponse
        extends AbstractCommandResponse<RetrieveConnectionMetricsResponse>
        implements ConnectivityQueryCommandResponse<RetrieveConnectionMetricsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ConnectivityCommandResponse.TYPE_PREFIX + RetrieveConnectionMetrics.NAME;

    private final ConnectionId connectionId;
    private final JsonObject jsonObject;

    private RetrieveConnectionMetricsResponse(final ConnectionId connectionId, final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);

        this.connectionId = checkNotNull(connectionId, "Connection ID");
        this.jsonObject = jsonObject;
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionMetricsResponse}.
     *
     * @param connectionId the identifier of the connection.
     * @param jsonObject the connection metrics jsonObject.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionMetricsResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionMetricsResponse of(final ConnectionId connectionId, final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new RetrieveConnectionMetricsResponse(connectionId, jsonObject, dittoHeaders);
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
                    final ConnectionId connectionId = ConnectionId.of(readConnectionId);

                    return of(connectionId, jsonObject, dittoHeaders);
                });
    }

    public Optional<Boolean> getContainsFailure() {
        return jsonObject.getValue(JsonFields.CONTAINS_FAILURES);
    }

    /**
     * Returns the retrieved {@code ConnectionMetrics}.
     *
     * @return the ConnectionMetrics.
     */
    public ConnectionMetrics getConnectionMetrics() {
        return ConnectivityModelFactory.connectionMetricsFromJson(
                jsonObject.getValue(JsonFields.CONNECTION_METRICS).orElse(JsonObject.empty()));
    }

    /**
     * Returns the retrieved {@code SourceMetrics}.
     *
     * @return the SourceMetrics.
     */
    public SourceMetrics getSourceMetrics() {
        return ConnectivityModelFactory.sourceMetricsFromJson(
                jsonObject.getValue(JsonFields.SOURCE_METRICS).orElse(JsonObject.empty()));
    }

    /**
     * Returns the retrieved {@code TargetMetrics}.
     *
     * @return the TargetMetrics.
     */
    public TargetMetrics getTargetMetrics() {
        return ConnectivityModelFactory.targetMetricsFromJson(
                jsonObject.getValue(JsonFields.TARGET_METRICS).orElse(JsonObject.empty()));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, String.valueOf(connectionId),
                predicate);

        jsonObjectBuilder.setAll(jsonObject);
    }

    @Override
    public ConnectionId getConnectionEntityId() {
        return connectionId;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return jsonObject;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/metrics");
    }

    @Override
    public RetrieveConnectionMetricsResponse setEntity(final JsonValue entity) {
        return of(connectionId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveConnectionMetricsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, jsonObject, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveConnectionMetricsResponse);
    }

    public static Builder getBuilder(final ConnectionId connectionId, final DittoHeaders dittoHeaders){
        return new Builder(connectionId, dittoHeaders);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RetrieveConnectionMetricsResponse that = (RetrieveConnectionMetricsResponse) o;
        return connectionId.equals(that.connectionId) &&
                jsonObject.equals(that.jsonObject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, jsonObject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                " connectionId=" + connectionId +
                ", jsonObject=" + jsonObject +
                "]";
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code RetrieveConnectionMetricsResponse}.
     */
    @Immutable
    static final class JsonFields {

        /**
         * Whether the connection metrics contain any failures - derived from connectionMetrics.
         */
        static final JsonFieldDefinition<Boolean> CONTAINS_FAILURES =
                JsonFactory.newBooleanFieldDefinition("containsFailures", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        static final JsonFieldDefinition<JsonObject> CONNECTION_METRICS =
                JsonFactory.newJsonObjectFieldDefinition("connectionMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        static final JsonFieldDefinition<JsonObject> SOURCE_METRICS =
                JsonFactory.newJsonObjectFieldDefinition("sourceMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        static final JsonFieldDefinition<JsonObject> TARGET_METRICS =
                JsonFactory.newJsonObjectFieldDefinition("targetMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }

    /**
     * Builder for {@code RetrieveConnectionMetricsResponse}.
     */
    @NotThreadSafe
    public static final class Builder {

        private final ConnectionId connectionId;
        private final DittoHeaders dittoHeaders;
        private boolean containsFailures; // derived from connectionMetrics
        private ConnectionMetrics connectionMetrics;
        private SourceMetrics sourceMetrics;
        private TargetMetrics targetMetrics;


        private Builder(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
            this.connectionId = checkNotNull(connectionId, "Connection ID");
            this.dittoHeaders = checkNotNull(dittoHeaders, "Ditto Headers");
        }

        public Builder connectionMetrics(final ConnectionMetrics connectionMetrics) {
            this.connectionMetrics = connectionMetrics;
            return this;
        }

        public Builder sourceMetrics(final SourceMetrics sourceMetrics) {
            this.sourceMetrics = sourceMetrics;
            return this;
        }

        public Builder targetMetrics(final TargetMetrics targetMetrics) {
            this.targetMetrics = targetMetrics;
            return this;
        }

        public RetrieveConnectionMetricsResponse build() {
            final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
            jsonObjectBuilder.set(CommandResponse.JsonFields.TYPE, TYPE);
            jsonObjectBuilder.set(CommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt());
            jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, String.valueOf(connectionId));

            if (connectionMetrics != null) {
                containsFailures = calculateWhetherContainsFailures(connectionMetrics);
                jsonObjectBuilder.set(JsonFields.CONNECTION_METRICS, connectionMetrics.toJson());
                jsonObjectBuilder.set(JsonFields.CONTAINS_FAILURES, containsFailures);
            }

            if (sourceMetrics != null) {
                jsonObjectBuilder.set(JsonFields.SOURCE_METRICS, sourceMetrics.toJson());
            }

            if (targetMetrics != null) {
                jsonObjectBuilder.set(JsonFields.TARGET_METRICS, targetMetrics.toJson());
            }

            return new RetrieveConnectionMetricsResponse(connectionId, jsonObjectBuilder.build(), dittoHeaders);
        }

        private static boolean calculateWhetherContainsFailures(final ConnectionMetrics connectionMetrics) {
            final boolean inboundContainsFailure = connectionMetrics.getInboundMetrics().getMeasurements()
                    .stream()
                    .filter(measurement -> !measurement.isSuccess())
                    .anyMatch(measurement -> measurement.getCounts().entrySet().stream().anyMatch(e -> e.getValue() > 0));
            final boolean outboundContainsFailure = connectionMetrics.getOutboundMetrics().getMeasurements()
                    .stream()
                    .filter(measurement -> !measurement.isSuccess())
                    .anyMatch(measurement -> measurement.getCounts().entrySet().stream().anyMatch(e -> e.getValue() > 0));
            return inboundContainsFailure || outboundContainsFailure;
        }

    }

}
