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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import java.util.List;

import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetricsResponse;

/**
 * Internal message wrapping all {@link AggregateThingsMetricsResponse}s for a single aggregation scrape cycle
 * of a specific metric. Enables the receiving actor to reconcile vanished group-by buckets by comparing the
 * batch contents against previously known gauges.
 * <p>
 * IMPORTANT: This record is intentionally not serializable. It is a local-only message that must only be sent
 * between co-located actors on the same JVM. Both {@link AggregateThingsMetricsActor} and
 * {@link OperatorAggregateMetricsProviderActor} are cluster singletons on the "search" role and are guaranteed
 * to run on the same node. If this co-location guarantee ever changes, this record must be made serializable
 * (e.g. by implementing {@code Jsonifiable} and registering a serializer).
 *
 * @param metricName the name of the metric this batch belongs to.
 * @param responses the list of aggregation responses for all group-by buckets returned in this scrape cycle.
 */
record AggregateThingsMetricsBatch(String metricName, List<AggregateThingsMetricsResponse> responses) {}
