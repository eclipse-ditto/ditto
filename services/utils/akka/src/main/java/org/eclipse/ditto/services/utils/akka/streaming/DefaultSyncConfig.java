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
package org.eclipse.ditto.services.utils.akka.streaming;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.DittoConfigError;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link SyncConfig}.
 */
@Immutable
public final class DefaultSyncConfig implements SyncConfig {

    private final boolean active;
    private final Duration startOffset;
    private final Duration initialStartOffset;
    private final Duration streamInterval;
    private final Duration outdatedWarningOffset;
    private final Duration outdatedErrorOffset;
    private final Duration maxIdleTime;
    private final Duration streamingActorTimeout;
    private final int batchSize;
    private final Duration minimalDelayBetweenStreams;

    private DefaultSyncConfig(final ConfigWithFallback syncScopedConfig) {
        active = syncScopedConfig.getBoolean(SyncConfigValue.ENABLED.getConfigPath());
        startOffset = syncScopedConfig.getDuration(SyncConfigValue.START_OFFSET.getConfigPath());
        initialStartOffset = syncScopedConfig.getDuration(SyncConfigValue.INITIAL_START_OFFSET.getConfigPath());
        streamInterval = syncScopedConfig.getDuration(SyncConfigValue.STREAM_INTERVAL.getConfigPath());
        outdatedWarningOffset = syncScopedConfig.getDuration(SyncConfigValue.OUTDATED_WARNING_OFFSET.getConfigPath());
        outdatedErrorOffset = syncScopedConfig.getDuration(SyncConfigValue.OUTDATED_ERROR_OFFSET.getConfigPath());
        maxIdleTime = syncScopedConfig.getDuration(SyncConfigValue.MAX_IDLE_TIME.getConfigPath());
        streamingActorTimeout = syncScopedConfig.getDuration(SyncConfigValue.STREAMING_ACTOR_TIMEOUT.getConfigPath());
        batchSize = syncScopedConfig.getInt(SyncConfigValue.ELEMENT_STREAM_BATCH_SIZE.getConfigPath());
        minimalDelayBetweenStreams =
                syncScopedConfig.getDuration(SyncConfigValue.MINIMAL_DELAY_BETWEEN_STREAMS.getConfigPath());
    }

    /**
     * Returns an instance of DefaultSyncConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the synchronization config at {@code configPath}.
     * Usually this is something like {@code "sync.things"}.
     * @param configPath the supposed path of the nested cache config settings.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSyncConfig getInstance(final Config config, final String configPath) {
        final DefaultSyncConfig result =
                new DefaultSyncConfig(ConfigWithFallback.newInstance(config, configPath, SyncConfigValue.values()));

        if (result.outdatedErrorOffset.compareTo(result.outdatedWarningOffset) <=0) {
            final String msgPattern = "Warning offset <{0}> is expected to be shorter than error offset <{1}>!";
            throw new DittoConfigError(
                    MessageFormat.format(msgPattern, result.outdatedErrorOffset, result.outdatedWarningOffset));
        }

        return result;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public Duration getStartOffset() {
        return startOffset;
    }

    @Override
    public Duration getInitialStartOffset() {
        return initialStartOffset;
    }

    @Override
    public Duration getStreamInterval() {
        return streamInterval;
    }

    @Override
    public Duration getOutdatedWarningOffset() {
        return outdatedWarningOffset;
    }

    @Override
    public Duration getOutdatedErrorOffset() {
        return outdatedErrorOffset;
    }

    @Override
    public Duration getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    public Duration getStreamingActorTimeout() {
        return streamingActorTimeout;
    }

    @Override
    public int getElementsStreamedPerBatch() {
        return batchSize;
    }

    @Override
    public Duration getMinimalDelayBetweenStreams() {
        return minimalDelayBetweenStreams;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultSyncConfig that = (DefaultSyncConfig) o;
        return active == that.active &&
                batchSize == that.batchSize &&
                Objects.equals(startOffset, that.startOffset) &&
                Objects.equals(initialStartOffset, that.initialStartOffset) &&
                Objects.equals(streamInterval, that.streamInterval) &&
                Objects.equals(outdatedWarningOffset, that.outdatedWarningOffset) &&
                Objects.equals(outdatedErrorOffset, that.outdatedErrorOffset) &&
                Objects.equals(maxIdleTime, that.maxIdleTime) &&
                Objects.equals(streamingActorTimeout, that.streamingActorTimeout) &&
                Objects.equals(minimalDelayBetweenStreams, that.minimalDelayBetweenStreams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(active, startOffset, initialStartOffset, streamInterval, outdatedWarningOffset,
                outdatedErrorOffset, maxIdleTime, streamingActorTimeout, batchSize, minimalDelayBetweenStreams);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "active=" + active +
                ", startOffset=" + startOffset +
                ", initialStartOffset=" + initialStartOffset +
                ", streamInterval=" + streamInterval +
                ", outdatedWarningOffset=" + outdatedWarningOffset +
                ", outdatedErrorOffset=" + outdatedErrorOffset +
                ", maxIdleTime=" + maxIdleTime +
                ", streamingActorTimeout=" + streamingActorTimeout +
                ", batchSize=" + batchSize +
                ", minimalDelayBetweenStreams=" + minimalDelayBetweenStreams +
                "]";
    }

}
