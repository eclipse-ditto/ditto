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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.junit.Test;

/**
 * Unit tests for {@link RetrieveAggregatedTimeseries}.
 */
public final class RetrieveAggregatedTimeseriesTest {

    private static final String NAMESPACE = "io.beyonnex.smartheating";
    private static final JsonPointer PATH =
            JsonPointer.of("/features/circuit/properties/flowTemperature");

    private static CrossThingTimeseriesQuery sampleQuery() {
        final List<GroupBy> groupBy = Arrays.asList(GroupBy.tag("building"), GroupBy.thingId());
        return CrossThingTimeseriesQuery.of(NAMESPACE, Collections.singletonList(PATH),
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                Duration.ofHours(1),
                Aggregation.AVG,
                groupBy,
                "eq(attributes/building,'A')",
                null, null, null);
    }

    private static DittoHeaders sampleHeaders() {
        return DittoHeaders.newBuilder()
                .correlationId("test-correlation-id")
                .responseRequired(true)
                .build();
    }

    @Test
    public void typeUsesTimeseriesPrefix() {
        assertThat(RetrieveAggregatedTimeseries.TYPE)
                .isEqualTo("timeseries.commands:retrieveAggregatedTimeseries");
        assertThat(RetrieveAggregatedTimeseries.NAME).isEqualTo("retrieveAggregatedTimeseries");
    }

    @Test
    public void factoryRetainsQueryAndHeaders() {
        final RetrieveAggregatedTimeseries underTest =
                RetrieveAggregatedTimeseries.of(sampleQuery(), sampleHeaders());

        assertThat(underTest.getQuery()).isEqualTo(sampleQuery());
        assertThat(underTest.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(underTest.getDittoHeaders()).isEqualTo(sampleHeaders());
    }

    @Test
    public void categoryIsQuery() {
        assertThat(RetrieveAggregatedTimeseries.of(sampleQuery(), sampleHeaders()).getCategory())
                .isEqualTo(Command.Category.QUERY);
    }

    /**
     * A cross-Thing command targets no single Thing, so it must not present itself as an
     * entity-scoped signal — otherwise the edge forwarder would try to route it through the
     * per-Thing timeseries shard region.
     */
    @Test
    public void isNotEntityScoped() {
        final RetrieveAggregatedTimeseries underTest =
                RetrieveAggregatedTimeseries.of(sampleQuery(), sampleHeaders());

        assertThat(underTest).isNotInstanceOf(
                org.eclipse.ditto.base.model.entity.id.WithEntityId.class);
        assertThat((Object) underTest.getResourcePath()).isEqualTo(JsonPointer.empty());
    }

    @Test
    public void resourceTypeIsThing() {
        // Enforcement resolves thing:/... resource keys, so the resource type must stay "thing".
        assertThat(RetrieveAggregatedTimeseries.of(sampleQuery(), sampleHeaders()).getResourceType())
                .isEqualTo("thing");
    }

    @Test
    public void jsonRoundTrips() {
        final RetrieveAggregatedTimeseries underTest =
                RetrieveAggregatedTimeseries.of(sampleQuery(), sampleHeaders());

        final JsonObject json = underTest.toJson();

        assertThat(RetrieveAggregatedTimeseries.fromJson(json, sampleHeaders())).isEqualTo(underTest);
    }

    @Test
    public void setDittoHeadersReplacesHeaders() {
        final RetrieveAggregatedTimeseries underTest =
                RetrieveAggregatedTimeseries.of(sampleQuery(), sampleHeaders());
        final DittoHeaders newHeaders = DittoHeaders.newBuilder().correlationId("other").build();

        final RetrieveAggregatedTimeseries updated = underTest.setDittoHeaders(newHeaders);

        assertThat(updated.getDittoHeaders()).isEqualTo(newHeaders);
        assertThat(updated.getQuery()).isEqualTo(underTest.getQuery());
    }

    @Test
    public void rejectsNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveAggregatedTimeseries.of(null, sampleHeaders()));
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveAggregatedTimeseries.of(sampleQuery(), null));
    }
}
