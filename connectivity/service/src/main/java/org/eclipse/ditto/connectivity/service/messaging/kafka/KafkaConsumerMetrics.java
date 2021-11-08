/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;

import akka.kafka.javadsl.Consumer;

/**
 * Gets the Apache Kafka Metrics from a ConsumerControl and creates/sets Kamon gauges accordingly.
 */
public final class KafkaConsumerMetrics {

    private final Consumer.Control consumerControl;
    private Map<MetricName, Gauge> gauges;

    private KafkaConsumerMetrics(final Consumer.Control consumerControl, final CharSequence connectionId,
            final String streamName) {

        this.consumerControl = consumerControl;
        createGauges(consumerControl, connectionId, streamName).thenAccept(gaugeMap -> {
            gauges = gaugeMap;
            reportMetrics();
        });
    }

    /**
     * Returns a new instance of {@code KafkaConsumerMetrics}.
     *
     * @param consumerControl the consumer control from which to retrieve the metrics.
     * @param connectionId the {@code connectionId} for which the metrics are applicable.
     * @param streamName the name of the stream for which the metrics are applicable.
     * @return the new instance.
     * @throws java.lang.NullPointerException if any argument is {@code null}.
     */
    static KafkaConsumerMetrics newInstance(final Consumer.Control consumerControl, final CharSequence connectionId,
            final String streamName) {

        checkNotNull(consumerControl, "consumerControl");
        checkNotNull(connectionId, "connectionId");
        checkNotNull(streamName, "streamName");

        return new KafkaConsumerMetrics(consumerControl, connectionId, streamName);
    }

    /**
     * Report metrics via Kamon gauges.
     */
    void reportMetrics() {
        consumerControl.getMetrics()
                .thenAccept(metrics -> metrics.values()
                        .stream()
                        .filter(metricContainsValue())
                        .forEach(metric -> gauges.get(metric.metricName()).set((Double) metric.metricValue())));
    }

    private static Predicate<Metric> metricContainsValue() {
        return metric -> !(metric.metricValue() instanceof String);
    }

    private static CompletionStage<Map<MetricName, Gauge>> createGauges(final Consumer.Control consumerControl,
            final CharSequence connectionId,
            final String streamName) {

        return consumerControl.getMetrics()
                .thenApply(metrics -> metrics.values()
                        .stream()
                        .collect(Collectors.toMap(Metric::metricName,
                                metric -> DittoMetrics.gauge(metric.metricName().name())
                                        .tag(ConnectionId.class.getSimpleName(), connectionId.toString())
                                        .tag("streamName", streamName))));

    }

}
