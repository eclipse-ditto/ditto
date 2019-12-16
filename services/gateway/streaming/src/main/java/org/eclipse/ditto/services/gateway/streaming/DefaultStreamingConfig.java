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

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the streaming config.
 */
@Immutable
public final class DefaultStreamingConfig implements StreamingConfig {

    private final Duration sessionCounterScrapeInterval;
    private final WebsocketConfig websocketConfig;
    private final String thingEnrichmentProvider;
    private final Config thingEnrichmentConfig;

    private DefaultStreamingConfig(final ScopedConfig scopedConfig) {
        sessionCounterScrapeInterval =
                scopedConfig.getDuration(StreamingConfigValue.SESSION_COUNTER_SCRAPE_INTERVAL.getConfigPath());
        websocketConfig = DefaultWebsocketConfig.of(scopedConfig);
        this.thingEnrichmentProvider =
                scopedConfig.getString(StreamingConfigValue.THING_ENRICHMENT_PROVIDER.getConfigPath());
        this.thingEnrichmentConfig =
                scopedConfig.getConfig(StreamingConfigValue.THING_ENRICHMENT_CONFIG.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultStreamingConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the web socket config at "streaming".
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
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
    public WebsocketConfig getWebsocketConfig() {
        return websocketConfig;
    }

    @Override
    public String getThingEnrichmentProvider() {
        return thingEnrichmentProvider;
    }

    @Override
    public Config getThingEnrichmentConfig() {
        return thingEnrichmentConfig;
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
        return Objects.equals(sessionCounterScrapeInterval, that.sessionCounterScrapeInterval) &&
                Objects.equals(thingEnrichmentProvider, that.thingEnrichmentProvider) &&
                Objects.equals(thingEnrichmentConfig, that.thingEnrichmentConfig) &&
                Objects.equals(websocketConfig, that.websocketConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionCounterScrapeInterval, thingEnrichmentProvider, thingEnrichmentConfig,
                websocketConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "sessionCounterScrapeInterval=" + sessionCounterScrapeInterval +
                ", thingEnrichmentProvider=" + thingEnrichmentProvider +
                ", thingEnrichmentConfig=" + thingEnrichmentConfig +
                ", websocketConfig=" + websocketConfig +
                "]";
    }
}
