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
package org.eclipse.ditto.thingsearch.model.signals.commands.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link AggregateThingsMetrics}.
 */
public final class AggregateThingsMetricsTest {

    private static final String METRIC_NAME = "test_metric";
    private static final Map<String, String> GROUPING_BY;
    static {
        final Map<String, String> map = new HashMap<>();
        map.put("location", "attributes/location");
        GROUPING_BY = Collections.unmodifiableMap(map);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(AggregateThingsMetrics.class)
                .withRedefinedSuperclass()
                .suppress(Warning.ALL_FIELDS_SHOULD_BE_USED) // type from AbstractCommand intentionally not in equals
                .verify();
    }

    @Test
    public void toJsonAndBackWithStringIndexHint() {
        final AggregateThingsMetrics command = AggregateThingsMetrics.of(
                METRIC_NAME, GROUPING_BY, "eq(attributes/type,sensor)",
                Collections.singletonList("ns1"), JsonValue.of("my_index"),
                DittoHeaders.empty());

        final String json = command.toJsonString();
        final AggregateThingsMetrics deserialized = AggregateThingsMetrics.fromJson(json, DittoHeaders.empty());

        assertThat(deserialized.getMetricName()).isEqualTo(METRIC_NAME);
        assertThat(deserialized.getIndexHint()).contains(JsonValue.of("my_index"));
    }

    @Test
    public void toJsonAndBackWithObjectIndexHint() {
        final JsonObject hint = JsonObject.newBuilder().set("t.attributes.location", 1).build();
        final AggregateThingsMetrics command = AggregateThingsMetrics.of(
                METRIC_NAME, GROUPING_BY, null,
                Collections.emptyList(), hint,
                DittoHeaders.empty());

        final String json = command.toJsonString();
        final AggregateThingsMetrics deserialized = AggregateThingsMetrics.fromJson(json, DittoHeaders.empty());

        assertThat(deserialized.getIndexHint()).isPresent();
        assertThat(deserialized.getIndexHint().get().isObject()).isTrue();
        assertThat(deserialized.getIndexHint().get().asObject()).isEqualTo(hint);
    }

    @Test
    public void toJsonAndBackWithoutIndexHint() {
        final AggregateThingsMetrics command = AggregateThingsMetrics.of(
                METRIC_NAME, GROUPING_BY, null,
                Collections.emptyList(),
                DittoHeaders.empty());

        final String json = command.toJsonString();
        final AggregateThingsMetrics deserialized = AggregateThingsMetrics.fromJson(json, DittoHeaders.empty());

        assertThat(deserialized.getIndexHint()).isEmpty();
    }

    @Test
    public void setDittoHeadersPreservesIndexHint() {
        final AggregateThingsMetrics command = AggregateThingsMetrics.of(
                METRIC_NAME, GROUPING_BY, null,
                Collections.emptyList(), JsonValue.of("my_index"),
                DittoHeaders.empty());

        final AggregateThingsMetrics withNewHeaders = command.setDittoHeaders(
                DittoHeaders.newBuilder().correlationId("test").build());

        assertThat(withNewHeaders.getIndexHint()).contains(JsonValue.of("my_index"));
    }
}
