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
import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.junit.Test;

/**
 * Unit tests for {@link RetrieveTimeseries}.
 */
public final class RetrieveTimeseriesTest {

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

    private static DittoHeaders sampleHeaders() {
        return DittoHeaders.newBuilder()
                .correlationId("test-correlation-id")
                .responseRequired(true)
                .build();
    }

    @Test
    public void typeUsesTimeseriesPrefix() {
        assertThat(RetrieveTimeseries.TYPE).isEqualTo("timeseries.commands:retrieveTimeseries");
        assertThat(RetrieveTimeseries.NAME).isEqualTo("retrieveTimeseries");
    }

    @Test
    public void factoryCreatesCommand() {
        final RetrieveTimeseries underTest = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());

        assertThat(underTest.getQuery()).isEqualTo(sampleQuery());
        assertThat((Object) underTest.getEntityId()).isEqualTo(THING_ID);
        assertThat(underTest.getDittoHeaders().getCorrelationId()).contains("test-correlation-id");
    }

    @Test
    public void factoryRejectsNullQuery() {
        assertThatNullPointerException().isThrownBy(() ->
                RetrieveTimeseries.of(null, sampleHeaders()));
    }

    @Test
    public void factoryRejectsNullHeaders() {
        assertThatNullPointerException().isThrownBy(() ->
                RetrieveTimeseries.of(sampleQuery(), null));
    }

    @Test
    public void getEntityIdReturnsThingIdFromQuery() {
        final RetrieveTimeseries underTest = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());

        assertThat((Object) underTest.getEntityId()).isEqualTo(THING_ID);
    }

    @Test
    public void getResourceTypeIsThing() {
        final RetrieveTimeseries underTest = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());

        assertThat(underTest.getResourceType()).isEqualTo("thing");
    }

    @Test
    public void getResourcePathIsRoot() {
        final RetrieveTimeseries underTest = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());

        final JsonPointer path = underTest.getResourcePath();
        assertThat((Object) path).isEqualTo(JsonPointer.empty());
    }

    @Test
    public void categoryIsQuery() {
        final RetrieveTimeseries underTest = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());

        assertThat(underTest.getCategory()).isEqualTo(Command.Category.QUERY);
    }

    @Test
    public void typePrefixUsesTimeseriesNamespace() {
        final RetrieveTimeseries underTest = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());

        assertThat(underTest.getTypePrefix()).isEqualTo("timeseries.commands:");
    }

    @Test
    public void setDittoHeadersReturnsNewCommandWithSwappedHeaders() {
        final RetrieveTimeseries underTest = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());
        final DittoHeaders newHeaders = DittoHeaders.newBuilder()
                .correlationId("other-id")
                .responseRequired(true)
                .build();

        final RetrieveTimeseries swapped = underTest.setDittoHeaders(newHeaders);

        assertThat(swapped.getQuery()).isEqualTo(sampleQuery());
        assertThat(swapped.getDittoHeaders().getCorrelationId()).contains("other-id");
    }

    @Test
    public void roundTripPreservesAllFields() {
        final RetrieveTimeseries original = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());

        final JsonObject json = original.toJson();
        final RetrieveTimeseries reconstructed =
                RetrieveTimeseries.fromJson(json, original.getDittoHeaders());

        assertThat(reconstructed).isEqualTo(original);
        assertThat(reconstructed.getQuery()).isEqualTo(original.getQuery());
    }

    @Test
    public void fromJsonRejectsMissingQuery() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("type", RetrieveTimeseries.TYPE)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> RetrieveTimeseries.fromJson(json, sampleHeaders()));
    }

    @Test
    public void equalsHonoursQueryAndHeaders() {
        final RetrieveTimeseries a = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());
        final RetrieveTimeseries b = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());
        final RetrieveTimeseries differentQuery = RetrieveTimeseries.of(
                TimeseriesQuery.of(THING_ID, Collections.singletonList(PATH),
                        Instant.parse("2026-01-13T00:00:00Z"),
                        Instant.parse("2026-01-14T00:00:00Z")),
                sampleHeaders());

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(differentQuery);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    public void toStringIncludesQuery() {
        final RetrieveTimeseries underTest = RetrieveTimeseries.of(sampleQuery(), sampleHeaders());

        assertThat(underTest.toString()).contains("query=").contains(THING_ID.toString());
    }
}
