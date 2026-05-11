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
package org.eclipse.ditto.timeseries.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;

/**
 * An immutable implementation of {@link TimeseriesDataPoint}.
 */
@Immutable
final class ImmutableTimeseriesDataPoint implements TimeseriesDataPoint {

    private final ThingId thingId;
    private final JsonPointer path;
    private final Instant timestamp;
    private final JsonValue value;
    private final long revision;
    private final Map<String, String> tags;
    @Nullable private final String unit;

    private ImmutableTimeseriesDataPoint(final ThingId thingId,
            final JsonPointer path,
            final Instant timestamp,
            final JsonValue value,
            final long revision,
            final Map<String, String> tags,
            @Nullable final String unit) {

        this.thingId = thingId;
        this.path = path;
        this.timestamp = timestamp;
        this.value = value;
        this.revision = revision;
        this.tags = tags;
        this.unit = unit;
    }

    static TimeseriesDataPoint of(final ThingId thingId,
            final JsonPointer path,
            final Instant timestamp,
            final JsonValue value,
            final long revision,
            final Map<String, String> tags,
            @Nullable final String unit) {

        checkNotNull(thingId, "thingId");
        checkNotNull(path, "path");
        checkNotNull(timestamp, "timestamp");
        checkNotNull(value, "value");
        checkNotNull(tags, "tags");
        // Reject array/object values at construction — timeseries storage expects scalar samples
        // (number / string / boolean / null). The downstream MongoDB Time Series collection's
        // implicit type binding cannot represent compound values without flattening, and the
        // aggregation paths in later phases assume one numeric / textual reading per timestamp.
        // Catching this at the model boundary surfaces the misuse before it reaches the adapter.
        // Note: Ditto's JsonNull deliberately reports both isObject() and isArray() as true so
        // it can stand in for either polymorphically — exclude null first so the legitimate
        // "absent reading" case isn't mis-rejected.
        if (!value.isNull() && (value.isObject() || value.isArray())) {
            throw new IllegalArgumentException(
                    "Timeseries values must be scalar (number/string/boolean/null); rejected " +
                            (value.isArray() ? "array" : "object") + " at path <" + path + "> for thing <" +
                            thingId + ">.");
        }

        return new ImmutableTimeseriesDataPoint(
                thingId,
                path,
                timestamp,
                value,
                revision,
                Collections.unmodifiableMap(new LinkedHashMap<>(tags)),
                unit);
    }

    static TimeseriesDataPoint fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(JsonFields.THING_ID));
        final JsonPointer path = JsonPointer.of(jsonObject.getValueOrThrow(JsonFields.PATH));
        final Instant timestamp = parseTimestamp(jsonObject.getValueOrThrow(JsonFields.TIMESTAMP));
        final JsonValue value = jsonObject.getValueOrThrow(JsonFields.VALUE);
        final long revision = jsonObject.getValueOrThrow(JsonFields.REVISION);
        final Map<String, String> tags = jsonObject.getValue(JsonFields.TAGS)
                .map(ImmutableTimeseriesDataPoint::tagsFromJson)
                .orElseGet(Collections::emptyMap);
        final String unit = jsonObject.getValue(JsonFields.UNIT).orElse(null);

        // Route through of() so the scalar-value invariant is enforced consistently with
        // programmatic construction. fromJson is the path used when a TimeseriesDataPoint crosses
        // a process boundary (cluster pub/sub, persistence read), and a malformed sender should
        // never bypass the model-level guard.
        return of(thingId, path, timestamp, value, revision, tags, unit);
    }

    private static Instant parseTimestamp(final String raw) {
        try {
            return Instant.parse(raw);
        } catch (final DateTimeParseException e) {
            throw new DittoJsonException(JsonParseException.newBuilder()
                    .message("Field <" + JsonFields.TIMESTAMP.getPointer() +
                            "> is not a valid ISO-8601 instant: <" + raw + ">.")
                    .description("Expected an ISO-8601 instant, e.g. \"2026-01-15T10:30:00Z\".")
                    .cause(e)
                    .build());
        }
    }

    private static Map<String, String> tagsFromJson(final JsonObject tagsJson) {
        final Map<String, String> result = new LinkedHashMap<>(tagsJson.getSize());
        for (final JsonField field : tagsJson) {
            final JsonValue tagValue = field.getValue();
            if (!tagValue.isString()) {
                throw new DittoJsonException(JsonParseException.newBuilder()
                        .message("Tag value for key <" + field.getKeyName() + "> must be a JSON string.")
                        .build());
            }
            result.put(field.getKeyName(), tagValue.asString());
        }
        return result;
    }

    @Override
    public ThingId getThingId() {
        return thingId;
    }

    @Override
    public JsonPointer getPath() {
        return path;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public JsonValue getValue() {
        return value;
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public Optional<String> getUnit() {
        return Optional.ofNullable(unit);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.THING_ID, thingId.toString())
                .set(JsonFields.PATH, path.toString())
                .set(JsonFields.TIMESTAMP, timestamp.toString())
                .set(JsonFields.VALUE, value)
                .set(JsonFields.REVISION, revision);

        if (!tags.isEmpty()) {
            final JsonObjectBuilder tagsBuilder = JsonFactory.newObjectBuilder();
            for (final Map.Entry<String, String> entry : tags.entrySet()) {
                tagsBuilder.set(entry.getKey(), entry.getValue());
            }
            builder.set(JsonFields.TAGS, tagsBuilder.build());
        }

        if (unit != null) {
            builder.set(JsonFields.UNIT, unit);
        }

        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableTimeseriesDataPoint)) {
            return false;
        }
        final ImmutableTimeseriesDataPoint that = (ImmutableTimeseriesDataPoint) o;
        return revision == that.revision &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(path, that.path) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(value, that.value) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, path, timestamp, value, revision, tags, unit);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", path=" + path +
                ", timestamp=" + timestamp +
                ", value=" + value +
                ", revision=" + revision +
                ", tags=" + tags +
                ", unit=" + unit +
                "]";
    }
}
