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
package org.eclipse.ditto.timeseries.mongodb;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;

/**
 * Maps a {@link TimeseriesDataPoint} into the BSON {@link Document} stored in a MongoDB Time Series
 * collection.
 * <p>
 * Document shape (matches the concept document, section 8.3.1):
 * <pre>
 * {
 *   "timestamp": ISODate("2026-01-15T10:30:00.000Z"),
 *   "meta": {
 *     "thingId": "org.eclipse.ditto:sensor-1",
 *     "path": "/features/env/properties/temperature",
 *     "tags": { "attributes/building": "A" },
 *     "unit": "cel"
 *   },
 *   "value": 23.5,
 *   "revision": 42
 * }
 * </pre>
 * <p>
 * Per the per-series type-binding rule from the pre-implementation review (issue #2291 comment),
 * only scalar JSON values (number, string, boolean, null) are accepted; objects and arrays are
 * rejected with an {@link IllegalArgumentException}, since timeseries backends require fixed types
 * per series.
 */
@Immutable
public final class TimeseriesBsonMapper {

    /** Top-level field name for the BSON Date timestamp. */
    static final String FIELD_TIMESTAMP = "timestamp";

    /** Top-level field name for the metadata sub-document. */
    static final String FIELD_META = "meta";

    /** Top-level field name for the captured value. */
    static final String FIELD_VALUE = "value";

    /** Top-level field name for the Thing's revision counter. */
    static final String FIELD_REVISION = "revision";

    /** Field name for the Thing ID inside {@code meta}. */
    static final String META_THING_ID = "thingId";

    /** Field name for the Ditto Protocol path inside {@code meta}. */
    static final String META_PATH = "path";

    /** Field name for the resolved-tags sub-document inside {@code meta}. */
    static final String META_TAGS = "tags";

    /** Field name for the unit string inside {@code meta}. */
    static final String META_UNIT = "unit";

    private TimeseriesBsonMapper() {
        throw new AssertionError();
    }

    /**
     * Converts a {@link TimeseriesDataPoint} to its BSON document representation.
     *
     * @param dataPoint the data point to convert.
     * @return the BSON document ready for insertion into the MongoDB Time Series collection.
     * @throws NullPointerException if {@code dataPoint} is {@code null}.
     * @throws IllegalArgumentException if the data point's value is a JSON array or JSON object
     * (only scalar values are stored in a timeseries).
     */
    public static Document toDocument(final TimeseriesDataPoint dataPoint) {
        checkNotNull(dataPoint, "dataPoint");

        final Document meta = buildMeta(dataPoint);

        final Document document = new Document();
        document.append(FIELD_TIMESTAMP, Date.from(dataPoint.getTimestamp()));
        document.append(FIELD_META, meta);
        document.append(FIELD_VALUE, convertScalarValue(dataPoint.getValue()));
        document.append(FIELD_REVISION, dataPoint.getRevision());
        return document;
    }

    private static Document buildMeta(final TimeseriesDataPoint dataPoint) {
        final Document meta = new Document();
        meta.append(META_THING_ID, dataPoint.getThingId().toString());
        meta.append(META_PATH, dataPoint.getPath().toString());

        final Map<String, String> tags = dataPoint.getTags();
        if (!tags.isEmpty()) {
            // LinkedHashMap to preserve declared order in the BSON document.
            meta.append(META_TAGS, new Document(new LinkedHashMap<>(tags)));
        }

        dataPoint.getUnit().ifPresent(unit -> meta.append(META_UNIT, unit));

        return meta;
    }

    /**
     * Converts a stored {@link Document} (read from a MongoDB Time Series collection) back into a
     * {@link TimeseriesDataValue}. The {@code _gap} flag is never set on a stored value: gaps are
     * synthesised at query time by a {@link org.eclipse.ditto.timeseries.model.FillStrategy}, not
     * persisted.
     *
     * @param document the BSON document.
     * @return the corresponding data value.
     * @throws NullPointerException if {@code document} is {@code null}.
     * @throws IllegalArgumentException if the document is missing the required fields or stores a
     * value type that cannot be expressed as a {@link JsonValue}.
     */
    public static TimeseriesDataValue toDataValue(final Document document) {
        checkNotNull(document, "document");

        final Object timestamp = document.get(FIELD_TIMESTAMP);
        if (!(timestamp instanceof Date)) {
            throw new IllegalArgumentException(
                    "Document is missing or has a non-Date <" + FIELD_TIMESTAMP + "> field.");
        }
        final Instant instant = ((Date) timestamp).toInstant();

        final Object raw = document.get(FIELD_VALUE);
        final JsonValue value = bsonValueToJsonValue(raw);
        return TimeseriesDataValue.of(instant, value);
    }

    /**
     * Returns the {@code meta.unit} value from a stored document, if present.
     */
    public static Optional<String> getStoredUnit(final Document document) {
        checkNotNull(document, "document");
        final Document meta = (Document) document.get(FIELD_META);
        if (meta == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(meta.getString(META_UNIT));
    }

    /**
     * Returns the {@code meta.path} value from a stored document, or {@code null} if absent.
     */
    @Nullable
    public static String getStoredPath(final Document document) {
        checkNotNull(document, "document");
        final Document meta = (Document) document.get(FIELD_META);
        return meta == null ? null : meta.getString(META_PATH);
    }

    /**
     * Returns the {@code meta.tags} of a stored document as a string map, preserving order; empty
     * when the document carries no tags.
     */
    public static Map<String, String> getStoredTags(final Document document) {
        checkNotNull(document, "document");
        final Document meta = (Document) document.get(FIELD_META);
        if (meta == null || !(meta.get(META_TAGS) instanceof Document tagsDoc)) {
            return Map.of();
        }
        final Map<String, String> result = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : tagsDoc.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return result;
    }

    private static JsonValue bsonValueToJsonValue(@Nullable final Object raw) {
        if (raw == null) {
            return JsonFactory.nullLiteral();
        }
        if (raw instanceof Boolean) {
            return JsonValue.of((boolean) raw);
        }
        if (raw instanceof Integer) {
            return JsonValue.of((int) raw);
        }
        if (raw instanceof Long) {
            return JsonValue.of((long) raw);
        }
        if (raw instanceof Double || raw instanceof Float) {
            return JsonValue.of(((Number) raw).doubleValue());
        }
        if (raw instanceof Number) {
            // BigDecimal / BigInteger / Decimal128 — fall back to double precision; lossless storage
            // requires the application to choose its scalar type up front.
            return JsonValue.of(((Number) raw).doubleValue());
        }
        if (raw instanceof CharSequence) {
            return JsonValue.of(raw.toString());
        }
        throw new IllegalArgumentException(
                "Unsupported stored timeseries value type: " + raw.getClass().getName());
    }

    /**
     * Converts a scalar {@link JsonValue} (number, string, boolean, null) into a BSON-compatible
     * Java primitive. Object and array values are rejected.
     */
    private static Object convertScalarValue(final JsonValue value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.isInt()) {
                return value.asInt();
            }
            if (value.isLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.isString()) {
            return value.asString();
        }
        // Object or array — reject. Per the per-series type-binding rule, complex values do not
        // belong in a timeseries; they belong in the event log.
        throw new IllegalArgumentException(
                "Cannot store timeseries value of complex JSON type (object or array): " + value +
                        ". Only number, string, boolean and null values are supported.");
    }
}
