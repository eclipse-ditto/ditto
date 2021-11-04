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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.Done;
import akka.kafka.javadsl.Consumer;

/**
 * Registry for collecting Kafka consumer metrics.
 */
public final class KafkaConsumerMetricsRegistry {

    @Nullable private static KafkaConsumerMetricsRegistry instance;

    private final Map<CacheKey, Consumer.DrainingControl<Done>> consumerControlMap;
    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(KafkaConsumerMetricsRegistry.class);

    private KafkaConsumerMetricsRegistry(final Duration metricCollectingInterval) {
        consumerControlMap = new HashMap<>();
        scheduleMetricReporting(metricCollectingInterval);
    }

    /**
     * Get an instance of the registry. Returns an already existing instance inf already created.
     *
     * @param metricCollectingInterval the interval in which to collect the metrics.
     * @return the instance.
     */
    public static KafkaConsumerMetricsRegistry getInstance(final Duration metricCollectingInterval) {
        if (null == instance) {
            instance = new KafkaConsumerMetricsRegistry(metricCollectingInterval);
        }
        return instance;
    }

    /**
     * Register a consumer for metric collecting.
     *
     * @param connectionId the connectionId the consumer belongs to.
     * @param consumerControl the control of the consumer.
     * @param streamName the name of the stream to which the metrics apply.
     */
    void registerConsumer(final ConnectionId connectionId, final Consumer.DrainingControl<Done> consumerControl,
            final String streamName) {

        consumerControlMap.put(new CacheKey(connectionId, streamName), consumerControl);
    }

    /**
     * Deregister a consumer for metric collecting.
     *
     * @param connectionId the connectionId the consumer belongs to.
     * @param streamName the name of the stream to which the metrics apply.
     */
    void deregisterConsumer(final ConnectionId connectionId, final String streamName) {
        consumerControlMap.remove(new CacheKey(connectionId, streamName));
    }

    private void scheduleMetricReporting(final Duration metricCollectingInterval) {
        new ScheduledThreadPoolExecutor(1).scheduleWithFixedDelay(this::reportMetrics,
                metricCollectingInterval.getSeconds(), metricCollectingInterval.getSeconds(), TimeUnit.SECONDS);
    }

    private void reportMetrics() {
        consumerControlMap.forEach((cacheKey, consumerControl) -> consumerControl.getMetrics()
                .thenAccept(KafkaConsumerMetricsRegistry::metricsToString));
    }

    private static void metricsToString(final Map<MetricName, Metric> metricMap) {
        final AtomicReference<String> metrics = new AtomicReference<>("");
        metricMap.forEach((metricName, metric) -> metrics.getAndSet(metrics.get()
                .concat(metricName.name() + " : " + metric.metricValue() + "\n")));
        LOGGER.info(metrics.get());
    }

    private static final class CacheKey {

        private final ConnectionId connectionId;
        private final String streamName;

        private CacheKey(final ConnectionId connectionId, final String streamName) {
            this.connectionId = connectionId;
            this.streamName = streamName;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (o instanceof CacheKey) {
                final var that = (CacheKey) o;
                return Objects.equals(connectionId, that.connectionId) &&
                        Objects.equals(streamName, that.streamName);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectionId, streamName);
        }

        @Override
        public String toString() {
            return String.format("%s:%s", connectionId, streamName);
        }
    }
}
