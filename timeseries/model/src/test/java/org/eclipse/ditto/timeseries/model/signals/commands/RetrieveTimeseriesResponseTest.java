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
package org.eclipse.ditto.timeseries.model.signals.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.junit.Test;

/**
 * Unit tests for {@link RetrieveTimeseriesResponse}.
 */
public final class RetrieveTimeseriesResponseTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH =
            JsonPointer.of("/features/environment/properties/temperature");

    private static TimeseriesQuery sampleQuery() {
        return TimeseriesQuery.of(
                THING_ID,
                Collections.singletonList(PATH),
                Instant.parse("2026-01-14T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"));
    }

    private static TimeseriesQueryResult sampleResult() {
        return TimeseriesQueryResult.of(
                THING_ID,
                PATH,
                sampleQuery(),
                TimeseriesResultMeta.of(2, "cel", "number"),
                Arrays.asList(
                        TimeseriesDataValue.of(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(22.3)),
                        TimeseriesDataValue.of(Instant.parse("2026-01-14T11:00:00Z"), JsonValue.of(22.1))));
    }

    private static DittoHeaders sampleHeaders() {
        return DittoHeaders.newBuilder().correlationId("test-correlation-id").build();
    }

    @Test
    public void typeUsesTimeseriesResponsesPrefix() {
        assertThat(RetrieveTimeseriesResponse.TYPE)
                .isEqualTo("timeseries.responses:retrieveTimeseries");
    }

    @Test
    public void factoryCreatesResponseWithSingleResult() {
        final RetrieveTimeseriesResponse underTest = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.singletonList(sampleResult()), sampleHeaders());

        assertThat((Object) underTest.getEntityId()).isEqualTo(THING_ID);
        final List<TimeseriesQueryResult> results = underTest.getResults();
        assertThat(results).hasSize(1);
        assertThat(underTest.getHttpStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void factoryAcceptsEmptyResults() {
        final RetrieveTimeseriesResponse underTest = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.emptyList(), sampleHeaders());

        assertThat(underTest.getResults()).isEmpty();
    }

    @Test
    public void factoryRejectsNullThingId() {
        assertThatNullPointerException().isThrownBy(() ->
                RetrieveTimeseriesResponse.of(null,
                        Collections.singletonList(sampleResult()), sampleHeaders()));
    }

    @Test
    public void factoryRejectsNullResults() {
        assertThatNullPointerException().isThrownBy(() ->
                RetrieveTimeseriesResponse.of(THING_ID, null, sampleHeaders()));
    }

    @Test
    public void factoryRejectsNullHeaders() {
        assertThatNullPointerException().isThrownBy(() ->
                RetrieveTimeseriesResponse.of(
                        THING_ID, Collections.singletonList(sampleResult()), null));
    }

    @Test
    public void getResultsReturnsAnUnmodifiableList() {
        final RetrieveTimeseriesResponse underTest = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.singletonList(sampleResult()), sampleHeaders());

        assertThat(underTest.getResults()).isUnmodifiable();
    }

    @Test
    public void getResourceTypeIsThing() {
        final RetrieveTimeseriesResponse underTest = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.singletonList(sampleResult()), sampleHeaders());

        assertThat(underTest.getResourceType()).isEqualTo("thing");
    }

    @Test
    public void setDittoHeadersReturnsNewResponseWithSwappedHeaders() {
        final RetrieveTimeseriesResponse underTest = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.singletonList(sampleResult()), sampleHeaders());
        final DittoHeaders newHeaders = DittoHeaders.newBuilder().correlationId("other-id").build();

        final RetrieveTimeseriesResponse swapped = underTest.setDittoHeaders(newHeaders);

        assertThat(swapped.getDittoHeaders().getCorrelationId()).contains("other-id");
        assertThat(swapped.getResults()).isEqualTo(underTest.getResults());
    }

    @Test
    public void roundTripPreservesAllFields() {
        final RetrieveTimeseriesResponse original = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.singletonList(sampleResult()), sampleHeaders());

        final JsonObject json = original.toJson();
        final RetrieveTimeseriesResponse reconstructed =
                RetrieveTimeseriesResponse.fromJson(json, original.getDittoHeaders());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void roundTripPreservesEmptyResults() {
        final RetrieveTimeseriesResponse original = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.emptyList(), sampleHeaders());

        final RetrieveTimeseriesResponse reconstructed =
                RetrieveTimeseriesResponse.fromJson(original.toJson(), original.getDittoHeaders());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void fromJsonRejectsMissingThingId() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("type", RetrieveTimeseriesResponse.TYPE)
                .set("status", 200)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> RetrieveTimeseriesResponse.fromJson(json, sampleHeaders()));
    }

    @Test
    public void fromJsonRejectsMissingResults() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("type", RetrieveTimeseriesResponse.TYPE)
                .set("status", 200)
                .set("thingId", THING_ID.toString())
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> RetrieveTimeseriesResponse.fromJson(json, sampleHeaders()));
    }

    @Test
    public void fromJsonRejectsNullJsonObject() {
        assertThatNullPointerException().isThrownBy(() ->
                RetrieveTimeseriesResponse.fromJson(null, sampleHeaders()));
    }

    @Test
    public void equalsHonoursThingIdAndResultsAndHeaders() {
        final RetrieveTimeseriesResponse a = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.singletonList(sampleResult()), sampleHeaders());
        final RetrieveTimeseriesResponse b = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.singletonList(sampleResult()), sampleHeaders());
        final RetrieveTimeseriesResponse different = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.emptyList(), sampleHeaders());

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(different);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    public void toStringIncludesThingId() {
        final RetrieveTimeseriesResponse underTest = RetrieveTimeseriesResponse.of(
                THING_ID, Collections.singletonList(sampleResult()), sampleHeaders());

        assertThat(underTest.toString()).contains(THING_ID.toString());
    }
}
