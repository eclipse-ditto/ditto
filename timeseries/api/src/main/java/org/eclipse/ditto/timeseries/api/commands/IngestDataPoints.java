/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.api.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;

/**
 * Internal cluster command sent from the Things service to the Timeseries shard region asking it
 * to persist a batch of {@link TimeseriesDataPoint}s for a single Thing.
 * <p>
 * Sharded by {@link ThingId} so that all data points for a Thing land in the same shard, giving
 * per-Thing ordering on the write path even with the publisher running on multiple Things-service
 * nodes. Not part of the public Ditto Protocol — there's no HTTP, WebSocket or Connectivity route
 * that exposes it; the only sender is {@code TimeseriesIngestPublisher} on Things-service nodes
 * and the only recipient is {@code TimeseriesIngestActor} on Timeseries-service nodes.
 *
 * <h2>Idempotency</h2>
 * The {@link DittoHeaders#getCorrelationId() correlation-id} doubles as the publisher's delivery
 * identifier. A retry carries the same correlation-id, letting the receiver distinguish a retry
 * from a genuinely new batch when (Phase 2) write-side dedup is added. Phase 1 writes
 * unconditionally and accepts a small duplicate-row window during publisher retries.
 */
@Immutable
@JsonParsableCommand(typePrefix = IngestDataPoints.TYPE_PREFIX, name = IngestDataPoints.NAME)
public final class IngestDataPoints extends AbstractCommand<IngestDataPoints> implements WithEntityId {

    /**
     * Type prefix for ingest commands. Distinct from the user-facing {@code timeseries.commands:}
     * prefix so this internal control message can never collide with — or be mistaken for — a
     * Ditto Protocol command someone forwarded by accident.
     */
    public static final String TYPE_PREFIX = "timeseries.ingest." + Command.TYPE_QUALIFIER + ":";

    /**
     * Name of this command.
     */
    public static final String NAME = "ingestDataPoints";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    /**
     * Resource type the policy enforcer would use, were this command ever evaluated against a
     * policy. Currently never enforced (publisher acts under the Things-service trust boundary)
     * but included for future-proofing when fine-grained writer permissions land.
     */
    public static final String RESOURCE_TYPE = "thing";

    private static final JsonFieldDefinition<String> JSON_THING_ID =
            JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonArray> JSON_DATA_POINTS =
            JsonFactory.newJsonArrayFieldDefinition("dataPoints", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final List<TimeseriesDataPoint> dataPoints;

    private IngestDataPoints(final ThingId thingId,
            final List<TimeseriesDataPoint> dataPoints,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.thingId = thingId;
        this.dataPoints = dataPoints;
    }

    /**
     * Returns a new {@code IngestDataPoints} command.
     *
     * @param thingId the Thing all data points in {@code dataPoints} belong to. Must equal the
     * {@code thingId} carried by every entry in {@code dataPoints}.
     * @param dataPoints the data points to persist. May be empty (no-op write); the receiver
     * still acks an empty batch so a publisher's retry timer doesn't fire.
     * @param dittoHeaders the headers — the {@code correlation-id} is treated as the delivery
     * identifier by the publisher's retry logic.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any data point's {@code thingId} differs from the
     * {@code thingId} parameter.
     */
    public static IngestDataPoints of(final ThingId thingId,
            final List<TimeseriesDataPoint> dataPoints,
            final DittoHeaders dittoHeaders) {

        checkNotNull(thingId, "thingId");
        checkNotNull(dataPoints, "dataPoints");
        checkNotNull(dittoHeaders, "dittoHeaders");
        for (final TimeseriesDataPoint dp : dataPoints) {
            if (!thingId.equals(dp.getThingId())) {
                throw new IllegalArgumentException(
                        "Data point thingId <" + dp.getThingId() + "> does not match command " +
                                "thingId <" + thingId + ">. A single IngestDataPoints batch must " +
                                "carry data for exactly one Thing so the shard router can route " +
                                "the whole batch to one shard.");
            }
        }
        return new IngestDataPoints(thingId,
                Collections.unmodifiableList(new ArrayList<>(dataPoints)),
                dittoHeaders);
    }

    /**
     * Parses an {@code IngestDataPoints} from JSON. Required by {@link JsonParsableCommand} so
     * the command can travel between cluster nodes via the JSON-Jsonifiable serializer.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the parsed command.
     */
    public static IngestDataPoints fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<IngestDataPoints>(TYPE, jsonObject).deserialize(() -> {
            final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(JSON_THING_ID));
            final JsonArray dataPointsArray = jsonObject.getValueOrThrow(JSON_DATA_POINTS);
            final List<TimeseriesDataPoint> parsed = new ArrayList<>(dataPointsArray.getSize());
            for (final JsonValue value : dataPointsArray) {
                parsed.add(TimeseriesDataPoint.fromJson(value.asObject()));
            }
            return of(thingId, parsed, dittoHeaders);
        });
    }

    /**
     * @return the data points carried by this command. The list is unmodifiable.
     */
    public List<TimeseriesDataPoint> getDataPoints() {
        return dataPoints;
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public IngestDataPoints setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new IngestDataPoints(thingId, dataPoints, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THING_ID, thingId.toString(), predicate);
        final JsonArrayBuilder arrayBuilder = JsonFactory.newArrayBuilder();
        for (final TimeseriesDataPoint dp : dataPoints) {
            arrayBuilder.add(dp.toJson());
        }
        jsonObjectBuilder.set(JSON_DATA_POINTS, arrayBuilder.build(), predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof IngestDataPoints;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final IngestDataPoints that = (IngestDataPoints) obj;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(dataPoints, that.dataPoints) &&
                super.equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, dataPoints);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", thingId=" + thingId +
                ", dataPointCount=" + dataPoints.size() + "]";
    }
}
