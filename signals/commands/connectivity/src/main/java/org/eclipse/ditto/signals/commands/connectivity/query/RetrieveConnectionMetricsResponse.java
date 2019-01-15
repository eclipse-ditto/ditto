/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
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

    private final String connectionId;
    private final ConnectionMetrics connectionMetrics;
    private final SourceMetrics sourceMetrics;
    private final TargetMetrics targetMetrics;

    private RetrieveConnectionMetricsResponse(final String connectionId, final ConnectionMetrics connectionMetrics,
            final SourceMetrics sourceMetrics, final TargetMetrics targetMetrics, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);

        this.connectionId = connectionId;
        this.connectionMetrics = connectionMetrics;
        this.sourceMetrics = sourceMetrics;
        this.targetMetrics = targetMetrics;
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
            final ConnectionMetrics connectionMetrics, final SourceMetrics sourceMetrics,
            final TargetMetrics targetMetrics, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(connectionMetrics, "Connection Status");

        return new RetrieveConnectionMetricsResponse(connectionId, connectionMetrics, sourceMetrics,
                targetMetrics, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionMetricsResponse}.
     *
     * @param connectionId the identifier of the connection.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionMetricsResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionMetricsResponse of(final String connectionId, final SourceMetrics sourceMetrics,
            final TargetMetrics targetMetrics, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        final AddressMetric fromSources = mergeAllMetrics(sourceMetrics.getAddressMetrics().values());
        final AddressMetric fromTargets = mergeAllMetrics(targetMetrics.getAddressMetrics().values());
        final ConnectionMetrics connectionMetrics = ConnectivityModelFactory.newConnectionMetrics(mergeAddressMetric(fromSources, fromTargets));
        return new RetrieveConnectionMetricsResponse(connectionId, connectionMetrics, sourceMetrics,
                targetMetrics, dittoHeaders);
    }

    private static AddressMetric mergeAllMetrics(final Collection<AddressMetric> metrics) {
        AddressMetric result = ConnectivityModelFactory.emptyAddressMetric();
        for (AddressMetric metric : metrics) {
            result = mergeAddressMetric(result, metric);
        }
        return result;
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
                            jsonObject.getValueOrThrow(JsonFields.JSON_CONNECTION_METRICS));
                    final SourceMetrics readSourceMetrics = ConnectivityModelFactory.sourceMetricsFromJson(
                            jsonObject.getValueOrThrow(JsonFields.JSON_SOURCE_METRICS));
                    final TargetMetrics readTargetMetrics = ConnectivityModelFactory.targetMetricsFromJson(
                            jsonObject.getValueOrThrow(JsonFields.JSON_TARGET_METRICS));

                    return of(readConnectionId, readConnectionMetrics, readSourceMetrics, readTargetMetrics,
                            dittoHeaders);
                });
    }

    /**
     * Merges the passed {@code other} RetrieveConnectionMetricsResponse with {@code this} response returning a new
     * RetrieveConnectionMetricsResponse containing the merged information.
     *
     * @param other the other RetrieveConnectionMetricsResponse to merge into {@code this} one.
     * @return the new merged RetrieveConnectionMetricsResponse.
     */
    public RetrieveConnectionMetricsResponse mergeWith(final RetrieveConnectionMetricsResponse other) {

        final SourceMetrics mergedSourceMetrics =
                ConnectivityModelFactory.newSourceMetrics(mergeAddressMetricMap(getSourceMetrics().getAddressMetrics(),
                        other.getSourceMetrics().getAddressMetrics()));

        final TargetMetrics mergedTargetMetrics =
                ConnectivityModelFactory.newTargetMetrics(mergeAddressMetricMap(getTargetMetrics().getAddressMetrics(),
                        other.getTargetMetrics().getAddressMetrics()));

        final ConnectionMetrics mergedConnectionMetrics =
                ConnectivityModelFactory.newConnectionMetrics(mergeAddressMetric(getConnectionMetrics().getMetrics(),
                        other.getConnectionMetrics().getMetrics()));

        return RetrieveConnectionMetricsResponse.of(connectionId, mergedConnectionMetrics, mergedSourceMetrics,
                mergedTargetMetrics, getDittoHeaders());
    }

    private static Map<String, AddressMetric> mergeAddressMetricMap(Map<String, AddressMetric> a,
            Map<String, AddressMetric> b) {
        final Map<String, AddressMetric> result = new HashMap<>(a);
        b.forEach((k, v) -> result.merge(k, v, RetrieveConnectionMetricsResponse::mergeAddressMetric));
        return result;
    }

    private static AddressMetric mergeAddressMetric(final AddressMetric a, AddressMetric b) {
        final Map<String, Measurement> mapA = asMap(a);
        final Map<String, Measurement> mapB = asMap(b);
        final Map<String, Measurement> result = new HashMap<>(mapA);
        mapB.forEach((keyFromA, measurementFromA) -> result.merge(keyFromA, measurementFromA,
                (measurementA, measurementB) -> {
                    final Map<Duration, Long> merged =
                            mergeMeasurements(measurementA.getCounts(), measurementB.getCounts());
                    return ConnectivityModelFactory.newMeasurement(measurementA.getType(), measurementA.isSuccess(),
                            merged, latest(
                                    measurementA.getLastMessageAt().orElse(null),
                                    measurementB.getLastMessageAt().orElse(null)
                            ));
                }));
        return ConnectivityModelFactory.newAddressMetric(new HashSet<>(result.values()));
    }

    @Nullable
    private static Instant latest(@Nullable final Instant instantA, @Nullable final Instant instantB) {
        if (instantA == null && instantB == null) {
            return null;
        } else if (instantA == null) {
            return instantB;
        } else if (instantB == null) {
            return instantA;
        } else {
            return Instant.ofEpochMilli(Math.max(instantA.toEpochMilli(), instantB.toEpochMilli()));
        }
    }

    private static Map<String, Measurement> asMap(final AddressMetric a) {
        return a.getMeasurements().stream().collect(Collectors.toMap(m -> m.getType() + ":" + m.isSuccess(), m -> m));
    }

    private static Map<Duration, Long> mergeMeasurements(final Map<Duration, Long> measurementA,
            final Map<Duration, Long> measurementB) {
        final Map<Duration, Long> result = new HashMap<>(measurementA);
        measurementB.forEach((k, v) -> result.merge(k, v, Long::sum));
        return result;
    }

    /**
     * Returns the retrieved {@code ConnectionMetrics}.
     *
     * @return the ConnectionMetrics.
     */
    public ConnectionMetrics getConnectionMetrics() {
        return connectionMetrics;
    }

    /**
     * Returns the retrieved {@code ConnectionMetrics}.
     *
     * @return the ConnectionMetrics.
     */
    public SourceMetrics getSourceMetrics() {
        return sourceMetrics;
    }

    /**
     * Returns the retrieved {@code ConnectionMetrics}.
     *
     * @return the ConnectionMetrics.
     */
    public TargetMetrics getTargetMetrics() {
        return targetMetrics;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_CONNECTION_METRICS, connectionMetrics.toJson(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_SOURCE_METRICS, sourceMetrics.toJson(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_TARGET_METRICS, targetMetrics.toJson(), predicate);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        appendPayload(jsonObjectBuilder, schemaVersion, field -> true);
        return jsonObjectBuilder.build();
    }

    @Override
    public RetrieveConnectionMetricsResponse setEntity(final JsonValue entity) {
        return fromJson(entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveConnectionMetricsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, connectionMetrics, sourceMetrics, targetMetrics, dittoHeaders);
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
                Objects.equals(connectionMetrics, that.connectionMetrics) &&
                Objects.equals(sourceMetrics, that.sourceMetrics) &&
                Objects.equals(targetMetrics, that.targetMetrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, connectionMetrics, sourceMetrics, targetMetrics);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                ", connectionMetrics=" + connectionMetrics +
                ", sourceMetrics=" + sourceMetrics +
                ", targetMetrics=" + targetMetrics +
                "]";
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code RetrieveConnectionMetricsResponse}.
     */
    @Immutable
    static final class JsonFields {

        static final JsonFieldDefinition<JsonObject> JSON_CONNECTION_METRICS =
                JsonFactory.newJsonObjectFieldDefinition("connectionMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        static final JsonFieldDefinition<JsonObject> JSON_SOURCE_METRICS =
                JsonFactory.newJsonObjectFieldDefinition("sourceMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        static final JsonFieldDefinition<JsonObject> JSON_TARGET_METRICS =
                JsonFactory.newJsonObjectFieldDefinition("targetMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }

}
