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
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementConfig;
import org.eclipse.ditto.edge.service.acknowledgements.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the streaming config.
 */
@Immutable
public final class DefaultStreamingConfig implements StreamingConfig {

    private final Duration sessionCounterScrapeInterval;
    private final int parallelism;
    private final AcknowledgementConfig acknowledgementConfig;
    private final Duration searchIdleTimeout;
    private final Duration subscriptionRefreshDelay;
    private final WebsocketConfig websocketConfig;
    private final SseConfig sseConfig;

    private DefaultStreamingConfig(final ScopedConfig scopedConfig) {
        sessionCounterScrapeInterval =
                scopedConfig.getNonNegativeAndNonZeroDurationOrThrow(
                        StreamingConfigValue.SESSION_COUNTER_SCRAPE_INTERVAL);
        parallelism = scopedConfig.getPositiveIntOrThrow(StreamingConfigValue.PARALLELISM);
        acknowledgementConfig = DefaultAcknowledgementConfig.of(scopedConfig);
        searchIdleTimeout = scopedConfig.getNonNegativeDurationOrThrow(StreamingConfigValue.SEARCH_IDLE_TIMEOUT);
        subscriptionRefreshDelay =
                scopedConfig.getNonNegativeDurationOrThrow(StreamingConfigValue.SUBSCRIPTION_REFRESH_DELAY);
        websocketConfig = DefaultWebsocketConfig.of(scopedConfig);
        sseConfig = DefaultSseConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of {@code DefaultStreamingConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the web socket config at "streaming".
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static StreamingConfig of(final Config config) {
        return new DefaultStreamingConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, StreamingConfigValue.values()));
    }

    @Override
    public Duration getSessionCounterScrapeInterval() {
        return sessionCounterScrapeInterval;
    }

    @Override
    public AcknowledgementConfig getAcknowledgementConfig() {
        return acknowledgementConfig;
    }

    @Override
    public WebsocketConfig getWebsocketConfig() {
        return websocketConfig;
    }

    @Override
    public SseConfig getSseConfig() {
        return sseConfig;
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public Duration getSearchIdleTimeout() {
        return searchIdleTimeout;
    }

    @Override
    public Duration getSubscriptionRefreshDelay() {
        return subscriptionRefreshDelay;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultStreamingConfig that = (DefaultStreamingConfig) o;
        return parallelism == that.parallelism &&
                Objects.equals(searchIdleTimeout, that.searchIdleTimeout) &&
                Objects.equals(subscriptionRefreshDelay, that.subscriptionRefreshDelay) &&
                Objects.equals(sessionCounterScrapeInterval, that.sessionCounterScrapeInterval) &&
                Objects.equals(acknowledgementConfig, that.acknowledgementConfig) &&
                Objects.equals(websocketConfig, that.websocketConfig) &&
                Objects.equals(sseConfig, that.sseConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parallelism, sessionCounterScrapeInterval, acknowledgementConfig, websocketConfig,
                sseConfig, searchIdleTimeout, subscriptionRefreshDelay);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "sessionCounterScrapeInterval=" + sessionCounterScrapeInterval +
                ", parallelism=" + parallelism +
                ", searchIdleTimeout=" + searchIdleTimeout +
                ", subscriptionRefreshDelay=" + subscriptionRefreshDelay +
                ", acknowledgementConfig=" + acknowledgementConfig +
                ", websocketConfig=" + websocketConfig +
                ", sseConfig=" + sseConfig +
                "]";
    }
}
