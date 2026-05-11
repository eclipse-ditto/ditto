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
package org.eclipse.ditto.protocol.adapter.things;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseriesResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Round-trip tests for {@link TimeseriesQueryCommandResponseAdapter}.
 */
public final class TimeseriesQueryCommandResponseAdapterTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH =
            JsonPointer.of("/features/environment/properties/temperature");

    private TimeseriesQueryCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = TimeseriesQueryCommandResponseAdapter.of(HeaderTranslator.empty());
    }

    @Test
    public void adapterIsForResponses() {
        assertThat(underTest.isForResponses()).isTrue();
        assertThat(underTest.getCriteria()).containsExactly(TopicPath.Criterion.TIMESERIES);
        assertThat(underTest.getActions()).containsExactly(TopicPath.Action.RETRIEVE);
    }

    @Test
    public void roundTripPreservesPayloadFields() {
        // The adapter framework augments headers (content-type, ditto-entity-id) on round-trip,
        // so equality on the full response would be brittle. Compare just the carried payload.
        final RetrieveTimeseriesResponse original = sampleResponse();

        final Adaptable adaptable = underTest.toAdaptable(original, TopicPath.Channel.TWIN);
        final RetrieveTimeseriesResponse reconstructed = underTest.fromAdaptable(adaptable);

        assertThat((Object) reconstructed.getEntityId()).isEqualTo(original.getEntityId());
        assertThat(reconstructed.getResults()).isEqualTo(original.getResults());
        assertThat(reconstructed.getHttpStatus()).isEqualTo(original.getHttpStatus());
    }

    @Test
    public void payloadCarriesHttpStatus() {
        final RetrieveTimeseriesResponse response = sampleResponse();

        final Adaptable adaptable = underTest.toAdaptable(response, TopicPath.Channel.TWIN);

        assertThat(adaptable.getPayload().getHttpStatus()).contains(HttpStatus.OK);
    }

    private static RetrieveTimeseriesResponse sampleResponse() {
        final TimeseriesQuery query = TimeseriesQuery.of(
                THING_ID,
                Collections.singletonList(PATH),
                Instant.parse("2026-01-14T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"));
        final TimeseriesQueryResult result = TimeseriesQueryResult.of(
                THING_ID,
                PATH,
                query,
                TimeseriesResultMeta.of(2, "cel", "number"),
                Arrays.asList(
                        TimeseriesDataValue.of(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(22.3)),
                        TimeseriesDataValue.of(Instant.parse("2026-01-14T11:00:00Z"), JsonValue.of(22.1))));
        return RetrieveTimeseriesResponse.of(THING_ID, Collections.singletonList(result),
                DittoHeaders.newBuilder().correlationId("test-resp").build());
    }
}
