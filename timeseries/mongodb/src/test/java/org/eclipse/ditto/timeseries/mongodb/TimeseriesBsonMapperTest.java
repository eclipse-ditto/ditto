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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.junit.Test;

/**
 * Unit tests for {@link TimeseriesBsonMapper}.
 */
public final class TimeseriesBsonMapperTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH =
            JsonPointer.of("/features/environment/properties/temperature");
    private static final Instant TIMESTAMP = Instant.parse("2026-01-15T10:30:00Z");
    private static final long REVISION = 42L;

    private static TimeseriesDataPoint dp(final JsonValue value, final Map<String, String> tags,
            final String unit) {

        return TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, value, REVISION, tags, unit);
    }

    @Test
    public void documentContainsAllTopLevelFields() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(23.5), Collections.emptyMap(), null));

        assertThat(doc.containsKey(TimeseriesBsonMapper.FIELD_TIMESTAMP)).isTrue();
        assertThat(doc.containsKey(TimeseriesBsonMapper.FIELD_META)).isTrue();
        assertThat(doc.containsKey(TimeseriesBsonMapper.FIELD_VALUE)).isTrue();
        assertThat(doc.containsKey(TimeseriesBsonMapper.FIELD_REVISION)).isTrue();
    }

    @Test
    public void timestampIsBsonDateMillisecondPrecision() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(0), Collections.emptyMap(), null));

        final Object ts = doc.get(TimeseriesBsonMapper.FIELD_TIMESTAMP);
        assertThat(ts).isInstanceOf(Date.class);
        assertThat(((Date) ts).toInstant()).isEqualTo(TIMESTAMP);
    }

    @Test
    public void metaContainsThingIdAndPath() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(0), Collections.emptyMap(), null));

        final Document meta = (Document) doc.get(TimeseriesBsonMapper.FIELD_META);
        assertThat(meta.getString(TimeseriesBsonMapper.META_THING_ID)).isEqualTo(THING_ID.toString());
        assertThat(meta.getString(TimeseriesBsonMapper.META_PATH)).isEqualTo(PATH.toString());
    }

    @Test
    public void metaOmitsTagsWhenEmpty() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(0), Collections.emptyMap(), null));

        final Document meta = (Document) doc.get(TimeseriesBsonMapper.FIELD_META);
        assertThat(meta.containsKey(TimeseriesBsonMapper.META_TAGS)).isFalse();
    }

    @Test
    public void metaContainsTagsWhenPresent() {
        final Map<String, String> tags = new LinkedHashMap<>();
        tags.put("attributes/building", "A");
        tags.put("attributes/floor", "2");

        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(0), tags, null));

        final Document meta = (Document) doc.get(TimeseriesBsonMapper.FIELD_META);
        final Document tagsDoc = (Document) meta.get(TimeseriesBsonMapper.META_TAGS);
        assertThat(tagsDoc.getString("attributes/building")).isEqualTo("A");
        assertThat(tagsDoc.getString("attributes/floor")).isEqualTo("2");
    }

    @Test
    public void metaOmitsUnitWhenAbsent() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(0), Collections.emptyMap(), null));

        final Document meta = (Document) doc.get(TimeseriesBsonMapper.FIELD_META);
        assertThat(meta.containsKey(TimeseriesBsonMapper.META_UNIT)).isFalse();
    }

    @Test
    public void metaContainsUnitWhenPresent() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(0), Collections.emptyMap(), "cel"));

        final Document meta = (Document) doc.get(TimeseriesBsonMapper.FIELD_META);
        assertThat(meta.getString(TimeseriesBsonMapper.META_UNIT)).isEqualTo("cel");
    }

    @Test
    public void revisionIsStoredAsLong() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(0), Collections.emptyMap(), null));

        assertThat(doc.get(TimeseriesBsonMapper.FIELD_REVISION)).isEqualTo(REVISION);
    }

    // --- Value type preservation ---

    @Test
    public void doubleValueIsStoredAsDouble() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(23.5), Collections.emptyMap(), null));

        assertThat(doc.get(TimeseriesBsonMapper.FIELD_VALUE)).isEqualTo(23.5);
    }

    @Test
    public void intValueIsStoredAsInteger() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(42), Collections.emptyMap(), null));

        assertThat(doc.get(TimeseriesBsonMapper.FIELD_VALUE)).isEqualTo(42);
    }

    @Test
    public void longValueIsStoredAsLong() {
        final long bigValue = 9_000_000_000L;

        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(bigValue), Collections.emptyMap(), null));

        assertThat(doc.get(TimeseriesBsonMapper.FIELD_VALUE)).isEqualTo(bigValue);
    }

    @Test
    public void stringValueIsStoredAsString() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of("ok"), Collections.emptyMap(), null));

        assertThat(doc.get(TimeseriesBsonMapper.FIELD_VALUE)).isEqualTo("ok");
    }

    @Test
    public void booleanValueIsStoredAsBoolean() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.of(true), Collections.emptyMap(), null));

        assertThat(doc.get(TimeseriesBsonMapper.FIELD_VALUE)).isEqualTo(true);
    }

    @Test
    public void nullValueIsStoredAsNullField() {
        final Document doc = TimeseriesBsonMapper.toDocument(
                dp(JsonValue.nullLiteral(), Collections.emptyMap(), null));

        assertThat(doc.get(TimeseriesBsonMapper.FIELD_VALUE)).isNull();
    }

    // --- Reject complex values (per per-series type-binding rule) ---

    // Note: array/object value rejection used to live in TimeseriesBsonMapper and was tested
    // here. Since IOT-495, the model layer (ImmutableTimeseriesDataPoint.of) enforces the same
    // invariant — non-scalar values can't even reach this mapper because the data point can't
    // be constructed. The model-level coverage lives in
    // ImmutableTimeseriesDataPointTest#factoryRejectsArrayValue / factoryRejectsObjectValue.

    @Test
    public void toDocumentRejectsNullDataPoint() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesBsonMapper.toDocument(null));
    }

    // --- toDataValue (read path) ---

    @Test
    public void toDataValueRoundTripsViaMappingPair() {
        final TimeseriesDataPoint dp = dp(JsonValue.of(23.5), Collections.emptyMap(), null);

        final org.bson.Document doc = TimeseriesBsonMapper.toDocument(dp);
        final org.eclipse.ditto.timeseries.model.TimeseriesDataValue value =
                TimeseriesBsonMapper.toDataValue(doc);

        assertThat(value.getTimestamp()).isEqualTo(dp.getTimestamp());
        assertThat(value.getValue()).contains(dp.getValue());
        assertThat(value.isGap()).isFalse();
    }

    @Test
    public void toDataValueRoundTripsNullValueAsExplicitNullLiteral() {
        // A null JSON value at ingestion (sensor reported JSON null) round-trips back as a
        // non-gap data value whose value is the JSON null literal — distinct from a gap, which
        // is a value entirely absent (Optional.empty()).
        final TimeseriesDataPoint dp =
                dp(JsonValue.nullLiteral(), Collections.emptyMap(), null);

        final org.eclipse.ditto.timeseries.model.TimeseriesDataValue value =
                TimeseriesBsonMapper.toDataValue(TimeseriesBsonMapper.toDocument(dp));

        assertThat(value.getValue()).contains(JsonValue.nullLiteral());
        assertThat(value.isGap()).isFalse();
    }

    @Test
    public void toDataValueRoundTripsBooleanValue() {
        final TimeseriesDataPoint dp = dp(JsonValue.of(true), Collections.emptyMap(), null);

        final org.eclipse.ditto.timeseries.model.TimeseriesDataValue value =
                TimeseriesBsonMapper.toDataValue(TimeseriesBsonMapper.toDocument(dp));

        assertThat(value.getValue()).contains(JsonValue.of(true));
    }

    @Test
    public void toDataValueRoundTripsStringValue() {
        final TimeseriesDataPoint dp = dp(JsonValue.of("ok"), Collections.emptyMap(), null);

        final org.eclipse.ditto.timeseries.model.TimeseriesDataValue value =
                TimeseriesBsonMapper.toDataValue(TimeseriesBsonMapper.toDocument(dp));

        assertThat(value.getValue()).contains(JsonValue.of("ok"));
    }

    @Test
    public void toDataValueRejectsMissingTimestamp() {
        final org.bson.Document docWithoutTimestamp = new org.bson.Document()
                .append(TimeseriesBsonMapper.FIELD_VALUE, 42);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TimeseriesBsonMapper.toDataValue(docWithoutTimestamp))
                .withMessageContaining("timestamp");
    }

    @Test
    public void toDataValueRejectsNullDocument() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesBsonMapper.toDataValue(null));
    }

    @Test
    public void getStoredUnitReturnsValueWhenPresent() {
        final TimeseriesDataPoint dp = dp(JsonValue.of(0), Collections.emptyMap(), "cel");

        final java.util.Optional<String> unit =
                TimeseriesBsonMapper.getStoredUnit(TimeseriesBsonMapper.toDocument(dp));

        assertThat(unit).contains("cel");
    }

    @Test
    public void getStoredUnitReturnsEmptyWhenAbsent() {
        final TimeseriesDataPoint dp = dp(JsonValue.of(0), Collections.emptyMap(), null);

        final java.util.Optional<String> unit =
                TimeseriesBsonMapper.getStoredUnit(TimeseriesBsonMapper.toDocument(dp));

        assertThat(unit).isEmpty();
    }

    @Test
    public void getStoredPathReturnsTheDittoProtocolPath() {
        final TimeseriesDataPoint dp = dp(JsonValue.of(0), Collections.emptyMap(), null);

        final String path = TimeseriesBsonMapper.getStoredPath(TimeseriesBsonMapper.toDocument(dp));

        assertThat(path).isEqualTo(PATH.toString());
    }
}
