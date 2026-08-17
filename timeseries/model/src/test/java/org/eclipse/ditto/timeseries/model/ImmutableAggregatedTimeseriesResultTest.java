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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableAggregatedTimeseriesResult}.
 */
public final class ImmutableAggregatedTimeseriesResultTest {

    private static final JsonPointer PATH =
            JsonPointer.of("/features/circuit/properties/flowTemperature");
    private static final TimeseriesResultMeta META = TimeseriesResultMeta.of(2, "cel", "number");
    private static final List<TimeseriesDataValue> DATA = Arrays.asList(
            TimeseriesDataValue.of(Instant.parse("2026-07-01T00:00:00Z"), JsonValue.of(21.5)),
            TimeseriesDataValue.of(Instant.parse("2026-07-01T01:00:00Z"), JsonValue.of(22.0)));

    private static Map<String, String> group() {
        final Map<String, String> group = new LinkedHashMap<>();
        group.put("building", "A");
        return group;
    }

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(ImmutableAggregatedTimeseriesResult.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithAllFields() {
        final AggregatedTimeseriesResult underTest =
                AggregatedTimeseriesResult.of(group(), PATH, META, DATA);

        assertThat(underTest.getGroup()).containsExactlyEntriesOf(group());
        assertThat((Object) underTest.getPath()).isEqualTo(PATH);
        assertThat(underTest.getMeta()).isEqualTo(META);
        assertThat(underTest.getData()).containsExactlyElementsOf(DATA);
    }

    @Test
    public void jsonRoundTripsWithGroup() {
        final AggregatedTimeseriesResult underTest =
                AggregatedTimeseriesResult.of(group(), PATH, META, DATA);

        assertThat(AggregatedTimeseriesResult.fromJson(underTest.toJson())).isEqualTo(underTest);
    }

    @Test
    public void jsonRoundTripsWithoutGroup() {
        final AggregatedTimeseriesResult underTest = AggregatedTimeseriesResult.of(
                Collections.<String, String>emptyMap(), PATH, META, DATA);

        assertThat(AggregatedTimeseriesResult.fromJson(underTest.toJson())).isEqualTo(underTest);
    }

    @Test
    public void jsonRoundTripsWithEmptyData() {
        final AggregatedTimeseriesResult underTest = AggregatedTimeseriesResult.of(group(), PATH,
                TimeseriesResultMeta.of(0, null, "number"),
                Collections.<TimeseriesDataValue>emptyList());

        assertThat(AggregatedTimeseriesResult.fromJson(underTest.toJson())).isEqualTo(underTest);
    }

    @Test
    public void toJsonOmitsEmptyGroup() {
        final JsonObject json = AggregatedTimeseriesResult.of(Collections.<String, String>emptyMap(),
                PATH, META, DATA).toJson();

        assertThat(json.getValue("group")).isEmpty();
    }

    @Test
    public void toJsonUsesResultKeyForMeta() {
        // The meta object is exposed as "result" for symmetry with TimeseriesQueryResult, whose
        // per-path entries already use that key — clients parse both with the same code.
        final JsonObject json = AggregatedTimeseriesResult.of(group(), PATH, META, DATA).toJson();

        assertThat(json.getValue("result")).isPresent();
        assertThat(json.getValue("path")).isPresent();
        assertThat(json.getValue("data")).isPresent();
    }

    @Test
    public void rejectsNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> AggregatedTimeseriesResult.of(null, PATH, META, DATA));
        assertThatNullPointerException()
                .isThrownBy(() -> AggregatedTimeseriesResult.of(group(), null, META, DATA));
        assertThatNullPointerException()
                .isThrownBy(() -> AggregatedTimeseriesResult.of(group(), PATH, null, DATA));
        assertThatNullPointerException()
                .isThrownBy(() -> AggregatedTimeseriesResult.of(group(), PATH, META, null));
    }

    @Test
    public void returnedCollectionsAreUnmodifiable() {
        final AggregatedTimeseriesResult underTest =
                AggregatedTimeseriesResult.of(group(), PATH, META, DATA);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.getGroup().put("floor", "2"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.getData().clear());
    }
}
