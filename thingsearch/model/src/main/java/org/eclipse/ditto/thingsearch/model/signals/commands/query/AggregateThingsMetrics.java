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

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

public class AggregateThingsMetrics implements WithDittoHeaders {

    private final String metricName;
    private final Map<String, String> groupingBy;
    private final Map<String, String> namedFilters;
    private final DittoHeaders dittoHeaders;
    private final Set<String> namespaces;

    private AggregateThingsMetrics(final String metricName, final Map<String, String> groupingBy, final Map<String, String> namedFilters, final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {
        this.metricName = metricName;
        this.groupingBy = groupingBy;
        this.namedFilters = namedFilters;
        this.namespaces = namespaces;
        this.dittoHeaders = dittoHeaders;
    }

    public static AggregateThingsMetrics of(final String metricName, final Map<String, String> groupingBy, final Map<String, String> namedFilters, final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {
        return new AggregateThingsMetrics(metricName, groupingBy, namedFilters, namespaces, dittoHeaders);
    }

    public String getMetricName() {
        return metricName;
    }

    public Map<String, String> getGroupingBy() {
        return groupingBy;
    }

    public Map<String, String> getNamedFilters() {
        return namedFilters;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final AggregateThingsMetrics that = (AggregateThingsMetrics) o;
        return Objects.equals(metricName, that.metricName) &&
                Objects.equals(groupingBy, that.groupingBy) &&
                Objects.equals(namedFilters, that.namedFilters) &&
                Objects.equals(dittoHeaders, that.dittoHeaders) &&
                Objects.equals(namespaces, that.namespaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricName, groupingBy, namedFilters, dittoHeaders, namespaces);
    }

    @Override
    public String toString() {
        return "AggregateThingsMetrics{" +
                "metricName='" + metricName + '\'' +
                ", groupingBy=" + groupingBy +
                ", namedFilters=" + namedFilters +
                ", dittoHeaders=" + dittoHeaders +
                ", namespaces=" + namespaces +
                '}';
    }
}
