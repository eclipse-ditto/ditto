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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

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

    private final Map<CacheKey, KafkaConsumerMetrics> metricsMap;
    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(KafkaConsumerMetricsRegistry.class);

    private final AtomicReference<Set<NewConsumer>> rememberForRegister = new AtomicReference<>(new HashSet<>());
    private final AtomicReference<Set<CacheKey>> rememberForDeregister = new AtomicReference<>(new HashSet<>());

    private KafkaConsumerMetricsRegistry(final Duration metricCollectingInterval) {
        metricsMap = new HashMap<>();
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
     * @param streamName the name of the stream to which the metrics apply.
     */
    void registerConsumer(final ConnectionId connectionId, final Consumer.DrainingControl<Done> consumerControl,
            final String streamName) {

        LOGGER.debug("Registering new consumer for metric reporting: <{}:{}>", connectionId, streamName);
        // No way to check whether consumerControl is ready, thus waiting for interval till next metric reporting.
        rememberForRegister.getAndUpdate(set -> {
            set.add(new NewConsumer(connectionId, streamName, consumerControl));
            return set;
        });
    }

    /**
     * Deregister a consumer for metric collecting.
     *
     * @param connectionId the connectionId the consumer belongs to.
     * @param streamName the name of the stream to which the metrics apply.
     */
    void deregisterConsumer(final ConnectionId connectionId, final String streamName) {
        LOGGER.debug("De-registering consumer for metric reporting: <{}:{}>", connectionId, streamName);
        // Since new consumer are registered in interval, de-registering should also work this way.
        // If i.e. a consumer would be de-registered before the registering interval is reached this would cause
        // concurrency issues.
        rememberForDeregister.getAndUpdate(set -> {
            set.add(new CacheKey(connectionId, streamName));
            return set;
        });
    }

    private void scheduleMetricReporting(final Duration metricCollectingInterval) {
        LOGGER.info("Scheduling Kafka metric reporting in interval of: <{}>", metricCollectingInterval);
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::reportMetrics,
                metricCollectingInterval.getSeconds(), metricCollectingInterval.getSeconds(), TimeUnit.SECONDS);
    }

    private void reportMetrics() {
        LOGGER.debug("Reporting metrics for Kafka consumer streams. <{}> consumer streams registered",
                metricsMap.size());

        registerNewKafkaConsumerMetrics();
        deregisterKafkaConsumerMetrics();
        metricsMap.forEach((cacheKey, kafkaConsumerMetrics) -> kafkaConsumerMetrics.reportMetrics());
    }

    private void registerNewKafkaConsumerMetrics() {
        rememberForRegister.getAndUpdate(
                set -> {
                    set.forEach(newConsumer -> metricsMap.put(
                            new CacheKey(newConsumer.connectionId, newConsumer.streamName),
                            KafkaConsumerMetrics.newInstance(newConsumer.consumerControl, newConsumer.connectionId,
                                    newConsumer.streamName)));
                    return new HashSet<>();
                });
    }

    private void deregisterKafkaConsumerMetrics() {
        rememberForDeregister.getAndUpdate(
                set -> {
                    set.forEach(newConsumer -> metricsMap.remove(
                            new CacheKey(newConsumer.connectionId, newConsumer.streamName)));
                    return new HashSet<>();
                });
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

    private static final class NewConsumer {

        private final ConnectionId connectionId;
        private final String streamName;
        private final Consumer.Control consumerControl;

        private NewConsumer(final ConnectionId connectionId, final String streamName,
                final Consumer.Control consumerControl) {

            this.connectionId = connectionId;
            this.streamName = streamName;
            this.consumerControl = consumerControl;
        }

    }

}
