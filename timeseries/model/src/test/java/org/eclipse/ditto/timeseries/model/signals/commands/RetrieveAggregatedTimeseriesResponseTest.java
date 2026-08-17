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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.junit.Test;

/**
 * Unit tests for {@link RetrieveAggregatedTimeseriesResponse}, with particular attention to the
 * authorization summary surviving the cluster round-trip: the response is built on the timeseries
 * service and re-serialized on the gateway, so anything dropped by {@code toJson}/{@code fromJson}
 * silently degrades a partial result into one that looks complete.
 */
public final class RetrieveAggregatedTimeseriesResponseTest {

    private static final String NAMESPACE = "io.beyonnex.smartheating";
    private static final JsonPointer PATH = JsonPointer.of("/features/circuit/properties/flow");
    private static final JsonPointer OTHER = JsonPointer.of("/features/circuit/properties/ret");

    private static List<AggregatedTimeseriesResult> results() {
        return List.of(AggregatedTimeseriesResult.of(Map.of("building", "A"), PATH,
                TimeseriesResultMeta.of(0, "cel", "number"), List.of()));
    }

    private static Map<String, Integer> withheld() {
        final Map<String, Integer> w = new LinkedHashMap<>();
        w.put(OTHER.toString(), 1);
        return w;
    }

    @Test
    public void withheldByPathSurvivesJsonRoundTrip() {
        final RetrieveAggregatedTimeseriesResponse original = RetrieveAggregatedTimeseriesResponse.of(
                NAMESPACE, results(), 4, 0, withheld(), DittoHeaders.empty());

        final JsonObject json = original.toJson();
        final RetrieveAggregatedTimeseriesResponse parsed =
                RetrieveAggregatedTimeseriesResponse.fromJson(json, DittoHeaders.empty());

        assertThat(parsed.getWithheldByPath()).containsExactlyEntriesOf(withheld());
        assertThat(parsed.isPartial()).isTrue();
        assertThat(parsed.getContributingThings()).isEqualTo(4);
        assertThat(parsed.getExcludedThings()).isZero();
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void withheldByPathIsPresentInTheHttpEntity() {
        final RetrieveAggregatedTimeseriesResponse response = RetrieveAggregatedTimeseriesResponse.of(
                NAMESPACE, results(), 4, 0, withheld(), DittoHeaders.empty());

        final JsonValue entity = response.getEntity(JsonSchemaVersion.V_2);

        assertThat(entity.isObject()).isTrue();
        final JsonObject auth = entity.asObject().getValue("authorization")
                .orElseThrow().asObject();
        assertThat(auth.getValue("partial").orElseThrow().asBoolean()).isTrue();
        // Look the key up literally, by iterating fields: getValue(<slash-bearing string>) would
        // interpret it as a pointer and could pass even if the key had been wrongly nested.
        final JsonObject withheldJson = auth.getValue("withheldByPath").orElseThrow().asObject();
        assertThat(withheldJson.getKeys()).extracting(Object::toString)
                .containsExactly(OTHER.toString());
        assertThat(withheldJson.stream()
                .filter(f -> f.getKeyName().equals(OTHER.toString()))
                .findFirst().orElseThrow().getValue().asInt()).isEqualTo(1);
    }

    @Test
    public void partialIsFalseAndMapEmptyWhenNothingWithheld() {
        final RetrieveAggregatedTimeseriesResponse response = RetrieveAggregatedTimeseriesResponse.of(
                NAMESPACE, results(), 4, 0, Collections.emptyMap(), DittoHeaders.empty());

        assertThat(response.isPartial()).isFalse();
        assertThat(response.getWithheldByPath()).isEmpty();
        assertThat(RetrieveAggregatedTimeseriesResponse.fromJson(response.toJson(),
                DittoHeaders.empty()).getWithheldByPath()).isEmpty();
    }

    @Test
    public void setEntityPreservesWithheldByPath() {
        final RetrieveAggregatedTimeseriesResponse response = RetrieveAggregatedTimeseriesResponse.of(
                NAMESPACE, results(), 4, 0, withheld(), DittoHeaders.empty());

        final RetrieveAggregatedTimeseriesResponse reset =
                response.setEntity(response.getEntity(JsonSchemaVersion.V_2));

        assertThat(reset.getWithheldByPath()).containsExactlyEntriesOf(withheld());
        assertThat(reset.isPartial()).isTrue();
    }

    @Test
    public void setDittoHeadersPreservesWithheldByPath() {
        final RetrieveAggregatedTimeseriesResponse response = RetrieveAggregatedTimeseriesResponse.of(
                NAMESPACE, results(), 4, 0, withheld(), DittoHeaders.empty());

        final RetrieveAggregatedTimeseriesResponse rehomed = response.setDittoHeaders(
                DittoHeaders.newBuilder().correlationId("x").build());

        assertThat(rehomed.getWithheldByPath()).containsExactlyEntriesOf(withheld());
        assertThat(rehomed.isPartial()).isTrue();
    }
}
