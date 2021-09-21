/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the MQTT protocol.
 */
@Immutable
public interface Amqp10Config {

    /**
     * Returns the configuration for AMQP 1.0 consumer.
     *
     * @return the configuration.
     */
    Amqp10ConsumerConfig getConsumerConfig();

    /**
     * Returns the configuration for AMQP 1.0 publisher.
     *
     * @return the configuration.
     */
    Amqp10PublisherConfig getPublisherConfig();

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
     * @return configuration of HMAC request-signing algorithms.
     */
    Map<String, String> getHmacAlgorithms();

    /**
     * The client actor asks all consumer actors after starting them for their resource status with this timeout.
     *
     * @return initial consumer resource status ask timeout
     */
    Duration getInitialConsumerStatusAskTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code Amqp10Config}.
     */
    enum Amqp10ConfigValue implements KnownConfigValue {

        /**
         * How many message producers to cache per client actor.
         */
        PRODUCER_CACHE_SIZE("producer-cache-size", 10),

        /**
         * Connect timeout for the AMQP 1.0 client.
         */
        GLOBAL_CONNECT_TIMEOUT("global-connect-timeout", Duration.ofSeconds(15)),

        /**
         * Send timeout for the AMQP 1.0 client.
         */
        GLOBAL_SEND_TIMEOUT("global-send-timeout", Duration.ofSeconds(60)),

        /**
         * Input buffer size for AMQP 1.0 consumers. Set to a small value to prevent flooding.
         */
        GLOBAL_REQUEST_TIMEOUT("global-request-timeout", Duration.ofSeconds(5)),

        /**
         * How many message producers to cache per client actor.
         */
        GLOBAL_PREFETCH_POLICY_ALL_COUNT("global-prefetch-policy-all-count", 10),

        /**
         * HMAC request-signing algorithms.
         */
        HMAC_ALGORITHMS("hmac-algorithms", Map.of()),

        /**
         * Initial consumer resource status ask timeout.
         */
        INITIAL_CONSUMER_RESOURCE_STATUS_ASK_TIMEOUT("initial-consumer-resource-status-ask-timeout",
                Duration.ofMillis(500));

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
