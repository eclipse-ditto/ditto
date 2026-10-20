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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.junit.Test;

/**
 * Unit tests for {@link IngestDataPoints}. Focuses on the cluster-message contract: JSON
 * round-trip, the thingId-equality invariant on the batch, and the WithEntityId hook the shard
 * router relies on.
 */
public final class IngestDataPointsTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final ThingId OTHER_THING_ID = ThingId.of("org.eclipse.ditto", "sensor-2");
    private static final JsonPointer PATH = JsonPointer.of("/features/env/properties/temperature");

    @Test
    public void getEntityIdReturnsThingIdForSharding() {
        // The shard router uses WithEntityId#getEntityId to pick a shard. Each Thing must route
        // to a single shard so per-Thing ordering holds; this test pins down that contract.
        final IngestDataPoints command = IngestDataPoints.of(THING_ID,
                List.of(samplePoint(THING_ID)), DittoHeaders.empty());

        assertThat((Object) command.getEntityId()).isEqualTo(THING_ID);
    }

    @Test
    public void emptyBatchIsAllowed() {
        // An empty batch is the documented no-op path that still acks back to the publisher.
        final IngestDataPoints command = IngestDataPoints.of(THING_ID,
                Collections.emptyList(), DittoHeaders.empty());

        assertThat(command.getDataPoints()).isEmpty();
    }

    @Test
    public void mismatchedThingIdInBatchRejected() {
        // A single batch must not span Things — otherwise the shard router would have to split
        // the batch, defeating the point of routing by ThingId.
        assertThatThrownBy(() -> IngestDataPoints.of(THING_ID,
                List.of(samplePoint(OTHER_THING_ID)), DittoHeaders.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(OTHER_THING_ID.toString())
                .hasMessageContaining(THING_ID.toString());
    }

    @Test
    public void jsonRoundTripPreservesAllFields() {
        // The cluster serializer round-trips Signal commands through JSON. If toJson/fromJson
        // disagree the message fails to deserialize on the receiving node and triggers a
        // DeadLetter. Pin the round-trip explicitly so a future field is not silently dropped.
        final TimeseriesDataPoint dp = samplePoint(THING_ID);
        final IngestDataPoints original = IngestDataPoints.of(THING_ID, List.of(dp),
                DittoHeaders.newBuilder().correlationId("round-trip-1").build());

        final IngestDataPoints parsed = IngestDataPoints.fromJson(original.toJson(),
                original.getDittoHeaders());

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getDataPoints()).containsExactly(dp);
    }

    @Test
    public void typePrefixDistinguishesIngestFromUserFacingCommands() {
        // The user-facing namespace is "timeseries.commands:". Ingest uses
        // "timeseries.ingest.commands:" to keep internal control messages from accidentally
        // overlapping with public Ditto Protocol commands.
        assertThat(IngestDataPoints.TYPE).startsWith("timeseries.ingest.commands:");
        assertThat(IngestDataPoints.TYPE).doesNotStartWith("timeseries.commands:");
    }

    @Test
    public void setDittoHeadersReturnsCommandWithSameBatch() {
        // Patterns.ask copies headers onto retries; verify setDittoHeaders does not lose the
        // batch (a regression we'd otherwise only catch in the retry path).
        final IngestDataPoints original = IngestDataPoints.of(THING_ID,
                List.of(samplePoint(THING_ID)), DittoHeaders.empty());

        final IngestDataPoints withHeaders = original.setDittoHeaders(
                DittoHeaders.newBuilder().correlationId("retry-1").build());

        assertThat(withHeaders.getDataPoints()).isEqualTo(original.getDataPoints());
        assertThat((Object) withHeaders.getEntityId()).isEqualTo(THING_ID);
        assertThat(withHeaders.getDittoHeaders().getCorrelationId()).contains("retry-1");
    }

    private static TimeseriesDataPoint samplePoint(final ThingId thingId) {
        return TimeseriesDataPoint.of(thingId, PATH, Instant.parse("2026-01-01T00:00:00Z"),
                JsonValue.of(21.5), 1L, Collections.emptyMap(), "Cel");
    }
}
