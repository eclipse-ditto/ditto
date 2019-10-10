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
package org.eclipse.ditto.services.gateway.streaming;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ThrottlingConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the web socket config.
 */
@Immutable
public final class DefaultWebsocketConfig implements WebsocketConfig {

    private final int subscriberBackpressureQueueSize;
    private final int publisherBackpressureBufferSize;
    private final Duration sessionCounterScrapeInterval;
    private final ThrottlingConfig throttlingConfig;

    private DefaultWebsocketConfig(final ScopedConfig scopedConfig) {
        subscriberBackpressureQueueSize =
                scopedConfig.getInt(WebsocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getConfigPath());
        publisherBackpressureBufferSize =
                scopedConfig.getInt(WebsocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath());
        sessionCounterScrapeInterval =
                scopedConfig.getDuration(WebsocketConfigValue.SESSION_COUNTER_SCRAPE_INTERVAL.getConfigPath());
        throttlingConfig = ThrottlingConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of {@code DefaultWebSocketConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the web socket config at "websocket".
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultWebsocketConfig of(final Config config) {
        return new DefaultWebsocketConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, WebsocketConfigValue.values()));
    }

    @Override
    public int getSubscriberBackpressureQueueSize() {
        return subscriberBackpressureQueueSize;
    }

    @Override
    public int getPublisherBackpressureBufferSize() {
        return publisherBackpressureBufferSize;
    }

    @Override
    public Duration getSessionCounterScrapeInterval() {
        return sessionCounterScrapeInterval;
    }

    @Override
    public ThrottlingConfig getThrottlingConfig() {
        return throttlingConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultWebsocketConfig that = (DefaultWebsocketConfig) o;
        return subscriberBackpressureQueueSize == that.subscriberBackpressureQueueSize &&
                publisherBackpressureBufferSize == that.publisherBackpressureBufferSize &&
                Objects.equals(sessionCounterScrapeInterval, that.sessionCounterScrapeInterval) &&
                Objects.equals(throttlingConfig, that.throttlingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriberBackpressureQueueSize, publisherBackpressureBufferSize,
                sessionCounterScrapeInterval, throttlingConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "subscriberBackpressureQueueSize=" + subscriberBackpressureQueueSize +
                ", publisherBackpressureBufferSize=" + publisherBackpressureBufferSize +
                ", sessionCounterScrapeInterval=" + sessionCounterScrapeInterval +
                ", throttlingConfig=" + throttlingConfig +
                "]";
    }

}
