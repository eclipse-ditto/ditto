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
package org.eclipse.ditto.internal.utils.persistentactors.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link PingConfig}.
 */
@Immutable
public final class DefaultPingConfig implements PingConfig {

    private static final String CONFIG_PATH = "persistence-ping";

    private final String journalTag;
    private final Duration initialDelay;
    private final Duration interval;
    private final RateConfig rateConfig;
    private final int readJournalBatchSize;
    private final StreamingOrder streamingOrder;

    private DefaultPingConfig(final ConfigWithFallback config, final RateConfig theRateConfig) {
        journalTag = config.getString(PingConfigValue.JOURNAL_TAG.getConfigPath());
        initialDelay = config.getNonNegativeDurationOrThrow(PingConfigValue.INITIAL_DELAY);
        interval = config.getNonNegativeAndNonZeroDurationOrThrow(PingConfigValue.INTERVAL);
        readJournalBatchSize = config.getPositiveIntOrThrow(PingConfigValue.READ_JOURNAL_BATCH_SIZE);
        streamingOrder = config.getEnum(StreamingOrder.class, PingConfigValue.STREAMING_ORDER.getConfigPath());
        rateConfig = theRateConfig;
    }

    /**
     * Returns an instance of {@code DefaultPingConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultPingConfig of(final Config config) {
        final var reconnectScopedConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, PingConfigValue.values());

        return new DefaultPingConfig(reconnectScopedConfig, DefaultRateConfig.of(reconnectScopedConfig));
    }

    @Override
    public String getJournalTag() {
        return journalTag;
    }

    @Override
    public Duration getInitialDelay() {
        return initialDelay;
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public int getReadJournalBatchSize() {
        return readJournalBatchSize;
    }

    @Override
    public RateConfig getRateConfig() {
        return rateConfig;
    }

    @Override
    public StreamingOrder getStreamingOrder() {
        return streamingOrder;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPingConfig that = (DefaultPingConfig) o;
        return Objects.equals(journalTag, that.journalTag) &&
                Objects.equals(initialDelay, that.initialDelay) &&
                Objects.equals(interval, that.interval) &&
                readJournalBatchSize == that.readJournalBatchSize &&
                Objects.equals(rateConfig, that.rateConfig) &&
                Objects.equals(streamingOrder, that.streamingOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(journalTag, initialDelay, interval, readJournalBatchSize, rateConfig, streamingOrder);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "journalTag=" + journalTag +
                ", initialDelay=" + initialDelay +
                ", interval=" + interval +
                ", readJournalBatchSize=" + readJournalBatchSize +
                ", rateConfig=" + rateConfig +
                ", streamingOrder=" + streamingOrder +
                "]";
    }

}
