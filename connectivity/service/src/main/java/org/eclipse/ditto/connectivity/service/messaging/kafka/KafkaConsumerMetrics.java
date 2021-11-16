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

import java.util.function.Predicate;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.kafka.javadsl.Consumer;

/**
 * Gets the Apache Kafka Metrics from a ConsumerControl and creates/sets Kamon gauges accordingly.
 */
public final class KafkaConsumerMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerMetrics.class);
    private static final String KAFKA_CONSUMER_METRIC_PREFIX = "kafka_consumer_";

    private final Consumer.Control consumerControl;
    private final ConnectionId connectionId;
    private final String consumerId;

    private KafkaConsumerMetrics(final Consumer.Control consumerControl, final ConnectionId connectionId,
            final String consumerId) {

        this.consumerControl = consumerControl;
        this.connectionId = connectionId;
        this.consumerId = consumerId;
    }

    /**
     * Returns a new instance of {@code KafkaConsumerMetrics}.
     *
     * @param consumerControl the consumer control from which to retrieve the metrics.
     * @param connectionId the {@code connectionId} for which the metrics are applicable.
     * @param consumerId the unique identifier of the consumer stream.
     * @return the new instance.
     * @throws java.lang.NullPointerException if any argument is {@code null}.
     */
    static KafkaConsumerMetrics newInstance(final Consumer.Control consumerControl, final ConnectionId connectionId,
            final String consumerId) {

        checkNotNull(consumerControl, "consumerControl");
        checkNotNull(connectionId, "connectionId");
        checkNotNull(consumerId, "consumerId");

        return new KafkaConsumerMetrics(consumerControl, connectionId, consumerId);
    }

    private static Predicate<Metric> metricContainsValue() {
        return metric -> !(metric.metricValue() instanceof String);
    }

    /**
     * Report metrics via Kamon gauges.
     */
    void reportMetrics() {
        try {
            consumerControl.getMetrics()
                    .thenAccept(metrics -> metrics.values()
                            .stream()
                            .filter(metricContainsValue())
                            .forEach(metric -> getGauge(metric.metricName()).set((Double) metric.metricValue())));
        } catch (final NullPointerException ex) {
            /*
             * When getMetrics() is called directly after establishing the connection, it can happen that a
             * NullPointerException is thrown. Seems to be a bug of kafka streaming.
             */

            LOGGER.info("Could not report consumer metrics for source <{}> of connection <{}>, because metrics were " +
                    "not available, yet.", consumerId, connectionId);
        }
    }

    private Gauge getGauge(final MetricName metricName) {
        return DittoMetrics.gauge(KAFKA_CONSUMER_METRIC_PREFIX + metricName.name().replace("-", "_"))
                .tag("connectionId", connectionId.toString())
                .tag("consumerId", consumerId);
    }

}
