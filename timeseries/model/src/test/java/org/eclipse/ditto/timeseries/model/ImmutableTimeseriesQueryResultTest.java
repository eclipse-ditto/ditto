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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableTimeseriesQueryResult}.
 */
public final class ImmutableTimeseriesQueryResultTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH =
            JsonPointer.of("/features/environment/properties/temperature");
    private static final TimeseriesQuery QUERY = TimeseriesQuery.of(
            THING_ID,
            Collections.singletonList(PATH),
            Instant.parse("2026-01-14T00:00:00Z"),
            Instant.parse("2026-01-15T00:00:00Z"));
    private static final TimeseriesResultMeta META = TimeseriesResultMeta.of(2, "cel", "number");
    private static final List<TimeseriesDataValue> DATA = Collections.unmodifiableList(Arrays.asList(
            TimeseriesDataValue.of(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(22.3)),
            TimeseriesDataValue.of(Instant.parse("2026-01-14T11:00:00Z"), JsonValue.of(22.1))));

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(ImmutableTimeseriesQueryResult.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithAllFields() {
        final TimeseriesQueryResult underTest =
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, META, DATA);

        assertThat((Object) underTest.getThingId()).isEqualTo(THING_ID);
        assertThat((Object) underTest.getPath()).isEqualTo(PATH);
        assertThat(underTest.getQuery()).isEqualTo(QUERY);
        assertThat(underTest.getMeta()).isEqualTo(META);
        final List<TimeseriesDataValue> actualData = underTest.getData();
        assertThat(actualData).containsExactlyElementsOf(DATA);
    }

    @Test
    public void factoryAcceptsEmptyData() {
        final TimeseriesResultMeta emptyMeta = TimeseriesResultMeta.of(0, null, "number");

        final TimeseriesQueryResult underTest =
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, emptyMeta, Collections.emptyList());

        assertThat(underTest.getData()).isEmpty();
    }

    @Test
    public void factoryRejectsNullThingId() {
        assertThatNullPointerException().isThrownBy(() ->
                TimeseriesQueryResult.of(null, PATH, QUERY, META, DATA));
    }

    @Test
    public void factoryRejectsNullPath() {
        assertThatNullPointerException().isThrownBy(() ->
                TimeseriesQueryResult.of(THING_ID, null, QUERY, META, DATA));
    }

    @Test
    public void factoryRejectsNullQuery() {
        assertThatNullPointerException().isThrownBy(() ->
                TimeseriesQueryResult.of(THING_ID, PATH, null, META, DATA));
    }

    @Test
    public void factoryRejectsNullMeta() {
        assertThatNullPointerException().isThrownBy(() ->
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, null, DATA));
    }

    @Test
    public void factoryRejectsNullData() {
        assertThatNullPointerException().isThrownBy(() ->
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, META, null));
    }

    @Test
    public void getDataReturnsAnUnmodifiableList() {
        final TimeseriesQueryResult underTest =
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, META, DATA);

        assertThat(underTest.getData()).isUnmodifiable();
    }

    @Test
    public void factoryDefensivelyCopiesData() {
        final List<TimeseriesDataValue> mutable = new ArrayList<>(DATA);

        final TimeseriesQueryResult underTest =
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, META, mutable);

        mutable.add(TimeseriesDataValue.of(Instant.now(), JsonValue.of(99)));

        assertThat(underTest.getData()).hasSize(2);
    }

    @Test
    public void toJsonContainsAllFields() {
        final TimeseriesQueryResult underTest =
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, META, DATA);

        final JsonObject json = underTest.toJson();

        assertThat(json.getValue("thingId")).contains(JsonValue.of(THING_ID.toString()));
        assertThat(json.getValue("path")).contains(JsonValue.of(PATH.toString()));
        assertThat(json.getValue("query")).isPresent();
        assertThat(json.getValue("result")).isPresent();
        assertThat(json.getValue("data")).isPresent();
    }

    @Test
    public void roundTripPreservesAllFields() {
        final TimeseriesQueryResult original =
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, META, DATA);

        final TimeseriesQueryResult reconstructed = TimeseriesQueryResult.fromJson(original.toJson());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void roundTripWithEmptyDataPreservesEquality() {
        final TimeseriesResultMeta emptyMeta = TimeseriesResultMeta.of(0, null, "number");
        final TimeseriesQueryResult original =
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, emptyMeta, Collections.emptyList());

        final TimeseriesQueryResult reconstructed = TimeseriesQueryResult.fromJson(original.toJson());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void fromJsonRejectsNonObjectDataElement() {
        final TimeseriesQueryResult original =
                TimeseriesQueryResult.of(THING_ID, PATH, QUERY, META, DATA);
        final JsonObject json = original.toJson();
        final JsonObject corrupted = JsonFactory.newObjectBuilder(json)
                .set("data", JsonFactory.newArrayBuilder().add("not-an-object").build())
                .build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesQueryResult.fromJson(corrupted));
    }

    @Test
    public void fromJsonRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesQueryResult.fromJson(null));
    }
}
