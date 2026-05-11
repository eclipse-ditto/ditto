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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableTimeseriesDataPoint}.
 */
public final class ImmutableTimeseriesDataPointTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH =
            JsonPointer.of("/features/environment/properties/temperature");
    private static final Instant TIMESTAMP = Instant.parse("2026-01-15T10:30:00Z");
    private static final JsonValue VALUE = JsonValue.of(23.5);
    private static final long REVISION = 42L;
    private static final Map<String, String> TAGS = sampleTags();
    private static final String UNIT = "cel";

    private static Map<String, String> sampleTags() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("attributes/building", "A");
        map.put("attributes/floor", "2");
        return Collections.unmodifiableMap(map);
    }

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(ImmutableTimeseriesDataPoint.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithAllFields() {
        final TimeseriesDataPoint underTest =
                TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, VALUE, REVISION, TAGS, UNIT);

        assertThat((Object) underTest.getThingId()).isEqualTo(THING_ID);
        assertThat((Object) underTest.getPath()).isEqualTo(PATH);
        assertThat(underTest.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat((Object) underTest.getValue()).isEqualTo(VALUE);
        assertThat(underTest.getRevision()).isEqualTo(REVISION);
        final Map<String, String> actualTags = underTest.getTags();
        assertThat(actualTags).containsAllEntriesOf(TAGS);
        final java.util.Optional<String> actualUnit = underTest.getUnit();
        assertThat(actualUnit).contains(UNIT);
    }

    @Test
    public void factoryAcceptsNullUnit() {
        final TimeseriesDataPoint underTest =
                TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, VALUE, REVISION, TAGS, null);

        assertThat(underTest.getUnit()).isEmpty();
    }

    @Test
    public void factoryAcceptsEmptyTags() {
        final TimeseriesDataPoint underTest = TimeseriesDataPoint.of(
                THING_ID, PATH, TIMESTAMP, VALUE, REVISION, Collections.emptyMap(), null);

        assertThat(underTest.getTags()).isEmpty();
    }

    @Test
    public void factoryRejectsNullThingId() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataPoint.of(
                null, PATH, TIMESTAMP, VALUE, REVISION, TAGS, UNIT));
    }

    @Test
    public void factoryRejectsNullPath() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataPoint.of(
                THING_ID, null, TIMESTAMP, VALUE, REVISION, TAGS, UNIT));
    }

    @Test
    public void factoryRejectsNullTimestamp() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataPoint.of(
                THING_ID, PATH, null, VALUE, REVISION, TAGS, UNIT));
    }

    @Test
    public void factoryRejectsNullValue() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataPoint.of(
                THING_ID, PATH, TIMESTAMP, null, REVISION, TAGS, UNIT));
    }

    @Test
    public void factoryRejectsNullTags() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataPoint.of(
                THING_ID, PATH, TIMESTAMP, VALUE, REVISION, null, UNIT));
    }

    @Test
    public void factoryRejectsObjectValue() {
        // The MongoDB Time Series collection's column-store layout cannot represent compound
        // values without flattening — and the aggregation path in later phases assumes one scalar
        // sample per timestamp. Catching this at the model boundary fails fast instead of letting
        // a misuse propagate to the adapter / wire format.
        final JsonValue objectValue = JsonObject.newBuilder().set("foo", "bar").build();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, objectValue, REVISION, TAGS, UNIT))
                .withMessageContaining("scalar")
                .withMessageContaining("object");
    }

    @Test
    public void factoryRejectsArrayValue() {
        final JsonValue arrayValue = JsonArray.newBuilder().add(1).add(2).build();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, arrayValue, REVISION, TAGS, UNIT))
                .withMessageContaining("scalar")
                .withMessageContaining("array");
    }

    @Test
    public void factoryAcceptsScalarValueTypes() {
        // String / boolean / null are valid (e.g. categorical or status timeseries) — only
        // arrays and objects are rejected.
        TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, JsonValue.of("UP"), REVISION, TAGS, UNIT);
        TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, JsonValue.of(true), REVISION, TAGS, UNIT);
        TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, JsonValue.nullLiteral(), REVISION, TAGS, UNIT);
    }

    @Test
    public void getTagsReturnsAnUnmodifiableMap() {
        final TimeseriesDataPoint underTest =
                TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, VALUE, REVISION, TAGS, UNIT);

        assertThat(underTest.getTags()).isUnmodifiable();
    }

    @Test
    public void factoryDefensivelyCopiesTags() {
        final Map<String, String> mutable = new LinkedHashMap<>();
        mutable.put("attributes/building", "A");

        final TimeseriesDataPoint underTest = TimeseriesDataPoint.of(
                THING_ID, PATH, TIMESTAMP, VALUE, REVISION, mutable, null);

        mutable.put("attributes/floor", "9");

        // The data point must not see the late-added entry.
        assertThat(underTest.getTags()).containsOnlyKeys("attributes/building");
    }

    @Test
    public void toJsonContainsAllNonDefaultFields() {
        final TimeseriesDataPoint underTest =
                TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, VALUE, REVISION, TAGS, UNIT);

        final JsonObject json = underTest.toJson();

        assertThat(json.getValue("thingId")).contains(JsonValue.of(THING_ID.toString()));
        assertThat(json.getValue("path")).contains(JsonValue.of(PATH.toString()));
        assertThat(json.getValue("timestamp")).contains(JsonValue.of("2026-01-15T10:30:00Z"));
        assertThat(json.getValue("value")).contains(VALUE);
        assertThat(json.getValue("revision")).contains(JsonValue.of(REVISION));
        assertThat(json.getValue("unit")).contains(JsonValue.of(UNIT));
        assertThat(json.getValue("tags")).isPresent();
    }

    @Test
    public void toJsonOmitsTagsWhenEmpty() {
        final TimeseriesDataPoint underTest = TimeseriesDataPoint.of(
                THING_ID, PATH, TIMESTAMP, VALUE, REVISION, Collections.emptyMap(), UNIT);

        final JsonObject json = underTest.toJson();

        assertThat(json.contains("tags")).isFalse();
    }

    @Test
    public void toJsonOmitsUnitWhenNull() {
        final TimeseriesDataPoint underTest = TimeseriesDataPoint.of(
                THING_ID, PATH, TIMESTAMP, VALUE, REVISION, TAGS, null);

        final JsonObject json = underTest.toJson();

        assertThat(json.contains("unit")).isFalse();
    }

    @Test
    public void roundTripPreservesAllFields() {
        final TimeseriesDataPoint original =
                TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, VALUE, REVISION, TAGS, UNIT);

        final JsonObject json = original.toJson();
        final TimeseriesDataPoint reconstructed = TimeseriesDataPoint.fromJson(json);

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void roundTripWithoutOptionalFieldsPreservesEquality() {
        final TimeseriesDataPoint original = TimeseriesDataPoint.of(
                THING_ID, PATH, TIMESTAMP, VALUE, REVISION, Collections.emptyMap(), null);

        final JsonObject json = original.toJson();
        final TimeseriesDataPoint reconstructed = TimeseriesDataPoint.fromJson(json);

        assertThat(reconstructed).isEqualTo(original);
        assertThat(reconstructed.getTags()).isEmpty();
        assertThat(reconstructed.getUnit()).isEmpty();
    }

    @Test
    public void fromJsonAppliesDefaultsForOptionalFields() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("thingId", THING_ID.toString())
                .set("path", PATH.toString())
                .set("timestamp", TIMESTAMP.toString())
                .set("value", VALUE)
                .set("revision", REVISION)
                .build();

        final TimeseriesDataPoint underTest = TimeseriesDataPoint.fromJson(json);

        assertThat(underTest.getTags()).isEmpty();
        assertThat(underTest.getUnit()).isEmpty();
    }

    @Test
    public void fromJsonRejectsMissingRequiredField() {
        final JsonObject jsonWithoutThingId = JsonFactory.newObjectBuilder()
                .set("path", PATH.toString())
                .set("timestamp", TIMESTAMP.toString())
                .set("value", VALUE)
                .set("revision", REVISION)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> TimeseriesDataPoint.fromJson(jsonWithoutThingId));
    }

    @Test
    public void fromJsonRejectsInvalidTimestamp() {
        final JsonObject jsonWithBadTimestamp = JsonFactory.newObjectBuilder()
                .set("thingId", THING_ID.toString())
                .set("path", PATH.toString())
                .set("timestamp", "not-an-instant")
                .set("value", VALUE)
                .set("revision", REVISION)
                .build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesDataPoint.fromJson(jsonWithBadTimestamp));
    }

    @Test
    public void fromJsonRejectsNonStringTagValue() {
        final JsonObject tagsJson = JsonFactory.newObjectBuilder()
                .set("attributes/floor", JsonValue.of(2))
                .build();
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("thingId", THING_ID.toString())
                .set("path", PATH.toString())
                .set("timestamp", TIMESTAMP.toString())
                .set("value", VALUE)
                .set("revision", REVISION)
                .set("tags", tagsJson)
                .build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesDataPoint.fromJson(json));
    }

    @Test
    public void fromJsonRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataPoint.fromJson(null));
    }

    @Test
    public void toStringIncludesAllFields() {
        final TimeseriesDataPoint underTest =
                TimeseriesDataPoint.of(THING_ID, PATH, TIMESTAMP, VALUE, REVISION, TAGS, UNIT);

        final String s = underTest.toString();

        assertThat(s)
                .contains(THING_ID.toString())
                .contains(PATH.toString())
                .contains(TIMESTAMP.toString())
                .contains(VALUE.toString())
                .contains(String.valueOf(REVISION))
                .contains(UNIT);
    }
}
