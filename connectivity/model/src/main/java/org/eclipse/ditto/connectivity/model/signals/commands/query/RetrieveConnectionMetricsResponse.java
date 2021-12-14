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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.connectivity.model.AddressMetric;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionMetrics;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Measurement;
import org.eclipse.ditto.connectivity.model.SourceMetrics;
import org.eclipse.ditto.connectivity.model.TargetMetrics;
import org.eclipse.ditto.connectivity.model.WithConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to a {@link RetrieveConnectionMetrics} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveConnectionMetricsResponse.TYPE)
public final class RetrieveConnectionMetricsResponse
        extends AbstractCommandResponse<RetrieveConnectionMetricsResponse>
        implements ConnectivityQueryCommandResponse<RetrieveConnectionMetricsResponse>, WithConnectionId,
        SignalWithEntityId<RetrieveConnectionMetricsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ConnectivityCommandResponse.TYPE_PREFIX + RetrieveConnectionMetrics.NAME;

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveConnectionMetricsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrieveConnectionMetricsResponse(
                                ConnectionId.of(jsonObject.getValueOrThrow(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID)),
                                jsonObject,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ConnectionId connectionId;
    private final JsonObject jsonObject;

    private RetrieveConnectionMetricsResponse(final ConnectionId connectionId,
            final JsonObject jsonObject,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveConnectionMetricsResponse.class),
                dittoHeaders);

        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.jsonObject = checkNotNull(jsonObject, "jsonObject");
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
    public static RetrieveConnectionMetricsResponse of(final ConnectionId connectionId,
            final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new RetrieveConnectionMetricsResponse(connectionId, jsonObject, HTTP_STATUS, dittoHeaders);
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
    public static RetrieveConnectionMetricsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
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

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID,
                connectionId.toString(),
                predicate);
        jsonObjectBuilder.setAll(jsonObject);
    }

    @Override
    public ConnectionId getEntityId() {
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
        return other instanceof RetrieveConnectionMetricsResponse;
    }

    public static Builder getBuilder(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
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
                JsonFieldDefinition.ofBoolean("containsFailures", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonObject> CONNECTION_METRICS =
                JsonFieldDefinition.ofJsonObject("connectionMetrics", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonObject> SOURCE_METRICS =
                JsonFieldDefinition.ofJsonObject("sourceMetrics", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonObject> TARGET_METRICS =
                JsonFieldDefinition.ofJsonObject("targetMetrics", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

    /**
     * Builder for {@code RetrieveConnectionMetricsResponse}.
     */
    @NotThreadSafe
    public static final class Builder {

        private final ConnectionId connectionId;
        private final DittoHeaders dittoHeaders;
        private ConnectionMetrics connectionMetrics;
        private SourceMetrics sourceMetrics;
        private TargetMetrics targetMetrics;


        private Builder(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
            this.connectionId = checkNotNull(connectionId, "connectionId");
            this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
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
            final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
            jsonObjectBuilder.set(CommandResponse.JsonFields.TYPE, TYPE);
            jsonObjectBuilder.set(CommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode());
            jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID,
                    String.valueOf(connectionId));

            if (connectionMetrics != null) {
                // derived from connectionMetrics
                jsonObjectBuilder.set(JsonFields.CONNECTION_METRICS, connectionMetrics.toJson());
                jsonObjectBuilder.set(JsonFields.CONTAINS_FAILURES,
                        calculateWhetherContainsFailures(connectionMetrics));
            }

            if (sourceMetrics != null) {
                jsonObjectBuilder.set(JsonFields.SOURCE_METRICS, sourceMetrics.toJson());
            }

            if (targetMetrics != null) {
                jsonObjectBuilder.set(JsonFields.TARGET_METRICS, targetMetrics.toJson());
            }

            return new RetrieveConnectionMetricsResponse(connectionId,
                    jsonObjectBuilder.build(),
                    HTTP_STATUS,
                    dittoHeaders);
        }

        private static boolean calculateWhetherContainsFailures(final ConnectionMetrics connectionMetrics) {
            final AddressMetric inboundMetrics = connectionMetrics.getInboundMetrics();
            final boolean inboundContainsFailure = containsFailure(inboundMetrics.getMeasurements());

            final AddressMetric outboundMetrics = connectionMetrics.getOutboundMetrics();
            final boolean outboundContainsFailure = containsFailure(outboundMetrics.getMeasurements());

            return inboundContainsFailure || outboundContainsFailure;
        }

        private static boolean containsFailure(final Set<Measurement> measurements) {
            return measurements.stream()
                    .filter(measurement -> !measurement.isSuccess())
                    .map(Measurement::getCounts)
                    .map(Map::entrySet)
                    .flatMap(Set::stream)
                    .map(Map.Entry::getValue)
                    .anyMatch(value -> 0 < value);
        }

    }

}
