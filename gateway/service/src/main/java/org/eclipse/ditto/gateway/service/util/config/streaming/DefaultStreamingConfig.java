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
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the streaming config.
 */
@Immutable
public final class DefaultStreamingConfig implements StreamingConfig {

    private static final Duration MIN_MAX_SESSION_LAG = Duration.ofSeconds(1L);

    private final Duration sessionCounterScrapeInterval;
    private final int parallelism;
    private final AcknowledgementConfig acknowledgementConfig;
    private final Duration searchIdleTimeout;
    private final Duration subscriptionRefreshDelay;
    private final Duration maxSessionLag;
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
        maxSessionLag = getMaxSessionLagOrThrow(scopedConfig);
        websocketConfig = DefaultWebsocketConfig.of(scopedConfig);
        sseConfig = DefaultSseConfig.of(scopedConfig);
    }

    private static Duration getMaxSessionLagOrThrow(final ScopedConfig scopedConfig) {
        final var maxSessionLag = scopedConfig.getNonNegativeDurationOrThrow(StreamingConfigValue.MAX_SESSION_LAG);
        if (!maxSessionLag.isZero() && maxSessionLag.compareTo(MIN_MAX_SESSION_LAG) < 0) {
            throw new DittoConfigError("The value of <" + scopedConfig.getConfigPath() + "." +
                    StreamingConfigValue.MAX_SESSION_LAG.getConfigPath() + "> must be 0s (disabled) or at least <" +
                    MIN_MAX_SESSION_LAG + ">, but was <" + maxSessionLag + ">.");
        }
        return maxSessionLag;
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
    public Duration getMaxSessionLag() {
        return maxSessionLag;
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
                Objects.equals(maxSessionLag, that.maxSessionLag) &&
                Objects.equals(sessionCounterScrapeInterval, that.sessionCounterScrapeInterval) &&
                Objects.equals(acknowledgementConfig, that.acknowledgementConfig) &&
                Objects.equals(websocketConfig, that.websocketConfig) &&
                Objects.equals(sseConfig, that.sseConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parallelism, sessionCounterScrapeInterval, acknowledgementConfig, websocketConfig,
                sseConfig, searchIdleTimeout, subscriptionRefreshDelay, maxSessionLag);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "sessionCounterScrapeInterval=" + sessionCounterScrapeInterval +
                ", parallelism=" + parallelism +
                ", searchIdleTimeout=" + searchIdleTimeout +
                ", subscriptionRefreshDelay=" + subscriptionRefreshDelay +
                ", maxSessionLag=" + maxSessionLag +
                ", acknowledgementConfig=" + acknowledgementConfig +
                ", websocketConfig=" + websocketConfig +
                ", sseConfig=" + sseConfig +
                "]";
    }
}
