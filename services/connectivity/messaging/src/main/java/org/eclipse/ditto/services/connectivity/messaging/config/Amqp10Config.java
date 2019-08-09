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

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the MQTT protocol.
 */
@Immutable
public interface Amqp10Config {

    /**
     * Returns the consumer throttling interval meaning in which duration may the configured
     * {@link #getConsumerThrottlingLimit() limit} be processed before throttling further messages.
     *
     * @return the consumer throttling interval.
     */
    Duration getConsumerThrottlingInterval();

    /**
     * Returns the consumer throttling limit defining processed messages per configured
     * {@link #getConsumerThrottlingInterval()}  interval}.
     *
     * @return the consumer throttling limit.
     */
    int getConsumerThrottlingLimit();

    /**
     * Returns how many reply-to addresses to cache.
     *
     * @return the reply-to cache size.
     */
    int getReplyToCacheSize();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code Amqp10Config}.
     */
    enum Amqp10ConfigValue implements KnownConfigValue {

        /**
         * The consumer throttling interval meaning in which duration may the configured
         * {@link #CONSUMER_THROTTLING_LIMIT limit} be processed before throttling further messages.
         */
        CONSUMER_THROTTLING_INTERVAL("consumer.throttling.interval", Duration.ofSeconds(1)),

        /**
         * The consumer throttling limit defining processed messages per configured
         * {@link #CONSUMER_THROTTLING_INTERVAL interval}.
         */
        CONSUMER_THROTTLING_LIMIT("consumer.throttling.limit", 100),

        /**
         * How many reply-to addresses to cache per client actor.
         */
        REPLY_TO_CACHE_SIZE("reply-to-cache-size", 10);

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
