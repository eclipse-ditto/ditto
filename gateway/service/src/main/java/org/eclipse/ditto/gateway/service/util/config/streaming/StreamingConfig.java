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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Provides configuration settings of general gateway streaming functionality.
 */
public interface StreamingConfig {

    /**
     * Config path relative to its parent.
     */
    String CONFIG_PATH = "streaming";

    /**
     * Returns the session counter update interval.
     *
     * @return the interval.
     */
    Duration getSessionCounterScrapeInterval();

    /**
     * Returns the config specific to Acknowledgements for gateway streaming sessions.
     *
     * @return the config.
     */
    AcknowledgementConfig getAcknowledgementConfig();

    /**
     * Returns the websocket config for streaming.
     *
     * @return the websocket config.
     */
    WebsocketConfig getWebsocketConfig();

    /**
     * Returns the SSE config.
     *
     * @return the SSE config.
     * @since 1.1.0
     */
    SseConfig getSseConfig();

    /**
     * Returns maximum number of stream elements to process in parallel.
     *
     * @return the parallelism.
     */
    int getParallelism();

    /**
     * Returns how long to wait before closing an idle search stream.
     *
     * @return the search idle timeout.
     */
    Duration getSearchIdleTimeout();

    /**
     * Returns the minimum delay before refreshing the Ditto pubsub subscriptions of a stream.
     *
     * @return the minimum delay.
     */
    Duration getSubscriptionRefreshDelay();

    /**
     * Render this object into a Config object from which a copy of this object can be constructed.
     *
     * @return a config representation.
     */
    default Config render() {
        final Map<String, Object> map = new HashMap<>();
        map.put(StreamingConfigValue.SESSION_COUNTER_SCRAPE_INTERVAL.getConfigPath(),
                getSessionCounterScrapeInterval().toMillis() + "ms");
        map.put(StreamingConfigValue.PARALLELISM.getConfigPath(), getParallelism());
        map.put(StreamingConfigValue.SEARCH_IDLE_TIMEOUT.getConfigPath(), getSearchIdleTimeout());
        map.put(StreamingConfigValue.SUBSCRIPTION_REFRESH_DELAY.getConfigPath(), getSubscriptionRefreshDelay());
        return ConfigFactory.parseMap(map)
                .withFallback(getWebsocketConfig().render())
                .atKey(CONFIG_PATH);
    }

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code StreamingConfig}.
     */
    enum StreamingConfigValue implements KnownConfigValue {

        /**
         * How often to update websocket session counter by counting child actors.
         */
        SESSION_COUNTER_SCRAPE_INTERVAL("session-counter-scrape-interval", Duration.ofSeconds(30L)),

        /**
         * Maximum number of stream elements to process in parallel.
         */
        PARALLELISM("parallelism", 64),

        /**
         * How long to wait before closing an idle search stream.
         */
        SEARCH_IDLE_TIMEOUT("search-idle-timeout", Duration.ofSeconds(45)),

        /**
         * Minimum delay before refreshing the Ditto pubsub subscriptions of a stream.
         */
        SUBSCRIPTION_REFRESH_DELAY("subscription-refresh-delay", Duration.ofMinutes(5));

        private final String path;
        private final Object defaultValue;

        StreamingConfigValue(final String thePath, final Object theDefaultValue) {
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
