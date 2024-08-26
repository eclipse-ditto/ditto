/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.thingsearch.model.signals.commands.query;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;

public class AggregateThingsMetricsResponse implements WithDittoHeaders {

    private final Map<String, String> groupedBy;

    private final Map<String, Long> result;

    private final DittoHeaders dittoHeaders;
    private final JsonObject aggregation;
    private final String metricName;
    private AggregateThingsMetricsResponse(final JsonObject aggregation, final DittoHeaders dittoHeaders,
            final String metricName, final Set<String> filterNames) {
        this.aggregation = aggregation;
        this.dittoHeaders = dittoHeaders;
        this.metricName = metricName;
        groupedBy = aggregation.getValue("_id")
                .map(json -> {
                    if (json.isObject()) {
                        return json.asObject().stream()
                                .collect(Collectors.toMap(o -> o.getKey().toString(),
                                        o1 -> o1.getValue().formatAsString()));
                    } else {
                        return new HashMap<String, String>();
                    }
                        }
                )
                .orElse(new HashMap<>());
        result = filterNames.stream().filter(aggregation::contains).collect(Collectors.toMap(key ->
                key, key -> aggregation.getValue(JsonPointer.of(key))
                .orElseThrow(getJsonMissingFieldExceptionSupplier(key))
                .asLong()));
        // value should always be a number as it will be used for the gauge value in the metrics
    }

    public static AggregateThingsMetricsResponse of(final JsonObject aggregation,
            final AggregateThingsMetrics aggregateThingsMetrics) {
        return of(aggregation, aggregateThingsMetrics.getDittoHeaders(), aggregateThingsMetrics.getMetricName(), aggregateThingsMetrics.getNamedFilters().keySet());
    }

    public static AggregateThingsMetricsResponse of(final JsonObject aggregation, final DittoHeaders dittoHeaders,
            final String metricName, final Set<String> filterNames) {
        return new AggregateThingsMetricsResponse(aggregation, dittoHeaders, metricName, filterNames);
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    public Map<String, String> getGroupedBy() {
        return groupedBy;
    }

    public Map<String, Long> getResult() {
        return result;
    }

    public String getMetricName() {
        return metricName;
    }

    private Supplier<RuntimeException> getJsonMissingFieldExceptionSupplier(String field) {
        return () -> JsonMissingFieldException.newBuilder().fieldName(field).build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final AggregateThingsMetricsResponse response = (AggregateThingsMetricsResponse) o;
        return Objects.equals(groupedBy, response.groupedBy) &&
                Objects.equals(result, response.result) &&
                Objects.equals(dittoHeaders, response.dittoHeaders) &&
                Objects.equals(aggregation, response.aggregation) &&
                Objects.equals(metricName, response.metricName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupedBy, result, dittoHeaders, aggregation, metricName);
    }

    @Override
    public String toString() {
        return "AggregateThingsMetricsResponse{" +
                "groupedBy=" + groupedBy +
                ", result=" + result +
                ", dittoHeaders=" + dittoHeaders +
                ", aggregation=" + aggregation +
                ", metricName='" + metricName + '\'' +
                '}';
    }
}
