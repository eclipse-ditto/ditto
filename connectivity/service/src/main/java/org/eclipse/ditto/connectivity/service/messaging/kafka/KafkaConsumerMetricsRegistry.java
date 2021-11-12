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

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.Done;
import akka.kafka.javadsl.Consumer;

/**
 * Registry for collecting Kafka consumer metrics.
 */
final class KafkaConsumerMetricsRegistry {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(KafkaConsumerMetricsRegistry.class);

    @Nullable private static KafkaConsumerMetricsRegistry instance;

    private final Map<CacheKey, KafkaConsumerMetrics> metricsMap;

    private KafkaConsumerMetricsRegistry(final Duration metricCollectingInterval) {
        metricsMap = new ConcurrentHashMap<>();
        scheduleMetricReporting(metricCollectingInterval);
    }

    /**
     * Get an instance of the registry. Returns an already existing instance inf already created.
     *
     * @param metricCollectingInterval the interval in which to collect the metrics.
     * @return the instance.
     * @throws NullPointerException if {@code metricCollectingInterval} is {@code null}.
     */
    public static KafkaConsumerMetricsRegistry getInstance(final Duration metricCollectingInterval) {
        checkNotNull(metricCollectingInterval, "metricCollectingInterval");
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
     * @param consumerId the unique identifier of the consumer stream.
     */
    void registerConsumer(final ConnectionId connectionId, final Consumer.DrainingControl<Done> consumerControl,
            final String consumerId) {
        LOGGER.debug("Registering new consumer for metric reporting: <{}:{}>", connectionId, consumerId);
        metricsMap.put(new CacheKey(connectionId, consumerId),
                KafkaConsumerMetrics.newInstance(consumerControl, connectionId, consumerId));
        consumerControl.streamCompletion()
                .whenComplete((done, error) -> deregisterConsumer(connectionId, consumerId));
    }

    /**
     * Deregister a consumer for metric collecting.
     *
     * @param connectionId the connectionId the consumer belongs to.
     * @param consumerId the unique identifier of the consumer stream.
     */
    private void deregisterConsumer(final ConnectionId connectionId, final String consumerId) {
        LOGGER.debug("De-registering consumer for metric reporting: <{}:{}>", connectionId, consumerId);
        metricsMap.remove(new CacheKey(connectionId, consumerId));
    }

    private void scheduleMetricReporting(final Duration metricCollectingInterval) {
        LOGGER.info("Scheduling Kafka metric reporting in interval of: <{}>", metricCollectingInterval);
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::reportMetrics,
                metricCollectingInterval.getSeconds(), metricCollectingInterval.getSeconds(), TimeUnit.SECONDS);
    }

    private void reportMetrics() {
        LOGGER.debug("Reporting metrics for Kafka consumer streams. <{}> consumer streams registered",
                metricsMap.size());
        metricsMap.forEach((cacheKey, kafkaConsumerMetrics) -> kafkaConsumerMetrics.reportMetrics());
    }

    private static final class CacheKey {

        private final ConnectionId connectionId;
        private final String consumerId;

        private CacheKey(final ConnectionId connectionId, final String consumerId) {
            this.connectionId = connectionId;
            this.consumerId = consumerId;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (o instanceof CacheKey) {
                final var that = (CacheKey) o;
                return Objects.equals(connectionId, that.connectionId) &&
                        Objects.equals(consumerId, that.consumerId);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectionId, consumerId);
        }

        @Override
        public String toString() {
            return String.format("%s:%s", connectionId, consumerId);
        }

    }

}
