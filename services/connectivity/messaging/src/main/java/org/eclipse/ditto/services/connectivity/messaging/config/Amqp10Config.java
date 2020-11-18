/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ThrottlingConfig;
import org.eclipse.ditto.services.connectivity.messaging.backoff.BackOffConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the MQTT protocol.
 */
@Immutable
public interface Amqp10Config {

    /**
     * Return whether rate limit based on throughput _and_ acknowledgements is enabled.
     *
     * @return whether rate limit is enabled.
     */
    boolean isConsumerRateLimitEnabled();

    /**
     * Return how many unacknowledged messages are allowed, including messages for which redelivery is requested.
     *
     * @return the maximum number of messages in flight.
     */
    int getConsumerMaxInFlight();

    /**
     * Return when to forget messages for which redelivery was requested (they may be consumed by another consumer).
     *
     * @return the duration a redelivery request is kept.
     */
    Duration getConsumerRedeliveryExpectationTimeout();

    /**
     * Returns the consumer throttling config.
     *
     * @return the config.
     */
    ThrottlingConfig getConsumerThrottlingConfig();

    /**
     * Returns the consumer throttling interval meaning in which duration may the configured
     * {@link #getConsumerThrottlingLimit() limit} be processed before throttling further messages.
     *
     * @return the consumer throttling interval.
     */
    default Duration getConsumerThrottlingInterval() {
        return getConsumerThrottlingConfig().getInterval();
    }

    /**
     * Returns the consumer throttling limit defining processed messages per configured
     * {@link #getConsumerThrottlingInterval()}  interval}.
     *
     * @return the consumer throttling limit.
     */
    default int getConsumerThrottlingLimit() {
        return getConsumerThrottlingConfig().getLimit();
    }

    /**
     * Returns how many message producers to cache.
     *
     * @return the message producer cache size.
     */
    int getProducerCacheSize();

    /**
     * Returns the backOff config used for the internal actors.
     *
     * @return the BackOffConfig.
     */
    BackOffConfig getBackOffConfig();

    /**
     * @return maximum number of messages buffered at the publisher actor before dropping them.
     */
    int getMaxQueueSize();

    /**
     * @return the number of possible parallel message publications per publisher actor.
     */
    int getPublisherParallelism();

    /**
     * Connect timeout for the AMQP 1.0 client.
     * <p>
     * Used as {@code "jms.connectTimeout"} value.
     * <p>
     * QPID JMS doc:
     * Timeout value that controls how long the client waits on Connection establishment before returning with an error.
     * By default the client waits 15 seconds for a connection to be established before failing.
     *
     * @return the connect timeout for the AMQP 1.0 client.
     */
    Duration getGlobalConnectTimeout();

    /**
     * Send timeout for the AMQP 1.0 client.
     * <p>
     * Used as {@code "jms.sendTimeout"} value.
     * <p>
     * QPID JMS doc:
     * Timeout value that controls how long the client waits on completion of a synchronous message send before
     * returning an error.
     * By default the client will wait indefinitely for a send to complete.
     *
     * @return the send timeout for the AMQP 1.0 client.
     */
    Duration getGlobalSendTimeout();

    /**
     * Request timeout for the AMQP 1.0 client.
     * <p>
     * Used as {@code "jms.requestTimeout"} value.
     * <p>
     * QPID JMS doc:
     * Timeout value that controls how long the client waits on completion of various synchronous interactions, such as
     * opening a producer or consumer, before returning an error. Does not affect synchronous message sends.
     * By default the client will wait indefinitely for a request to complete.
     *
     * @return the request timeout for the AMQP 1.0 client.
     */
    Duration getGlobalRequestTimeout();

    /**
     * Input buffer size for AMQP 1.0 consumers. Set to a small value to prevent flooding.
     * <p>
     * Used as {@code "jms.prefetchPolicy.all"} value.
     * <p>
     * QPID JMS doc:
     * Used to set all prefetch values at once.
     *
     * @return the input buffer size for AMQP 1.0 consumers.
     */
    int getGlobalPrefetchPolicyAllCount();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code Amqp10Config}.
     */
    enum Amqp10ConfigValue implements KnownConfigValue {

        /**
         * Whether consumer rate limit is enabled.
         */
        CONSUMER_RATE_LIMIT_ENABLED("consumer.rate-limit-enabled", true),

        /**
         * How many unacknowledged messages are allowed, including messages for which redelivery is requested.
         */
        CONSUMER_MAX_IN_FLIGHT("consumer.max-in-flight", 200),

        /**
         * When to forget messages for which redelivery was requested (they may be consumed by another consumer).
         */
        CONSUMER_REDELIVERY_EXPECTATION_TIMEOUT("consumer.redelivery-expectation-timeout", Duration.ofMinutes(2L)),

        /**
         * How many message producers to cache per client actor.
         */
        PRODUCER_CACHE_SIZE("producer-cache-size", 10),

        /**
         * How many messages to buffer in the publisher actor before dropping them.
         */
        MAX_QUEUE_SIZE("publisher.max-queue-size", 10),

        /**
         * How many messages will be published in parallel
         */
        MESSAGE_PUBLISHING_PARALLELISM("publisher.parallelism", 3),

        /**
         * Connect timeout for the AMQP 1.0 client.
         */
        GLOBAL_CONNECT_TIMEOUT("global-connect-timeout", Duration.ofSeconds(15)),

        /**
         * Send timeout for the AMQP 1.0 client.
         */
        GLOBAL_SEND_TIMEOUT("global-send-timeout", Duration.ofSeconds(2)),

        /**
         * Input buffer size for AMQP 1.0 consumers. Set to a small value to prevent flooding.
         */
        GLOBAL_REQUEST_TIMEOUT("global-request-timeout", Duration.ofSeconds(5)),

        /**
         * How many message producers to cache per client actor.
         */
        GLOBAL_PREFETCH_POLICY_ALL_COUNT("global-prefetch-policy-all-count", 10);

        private final String path;
        private final Object defaultValue;

        Amqp10ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

}
