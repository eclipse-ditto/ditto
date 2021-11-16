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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.komamitsu.fluency.Fluency;
import org.komamitsu.fluency.fluentd.FluencyBuilderForFluentd;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link FluencyLoggerPublisherConfig}.
 */
@Immutable
public final class DefaultFluencyLoggerPublisherConfig implements FluencyLoggerPublisherConfig {

    private static final String CONFIG_PATH = "fluency";

    private final String host;
    private final int port;
    private final boolean sslEnabled;
    private final Duration connectionTimeout;
    private final Duration readTimeout;
    private final long maxBufferSize;
    private final long bufferChunkInitialSize;
    private final long bufferChunkRetentionSize;
    private final Duration bufferChunkRetentionTime;
    private final Duration flushAttemptInterval;
    @Nullable private final String fileBackupDir;
    private final Duration waitUntilBufferFlushed;
    private final Duration waitUntilFlusherTerminated;
    private final boolean jvmHeapBufferMode;
    private final int senderMaxRetryCount;
    private final Duration senderBaseRetryInterval;
    private final Duration senderMaxRetryInterval;
    private final boolean ackResponseMode;
    private final Duration waitUntilAllBufferFlushedDurationOnClose;

    private DefaultFluencyLoggerPublisherConfig(final ConfigWithFallback config) {
        host = config.getString(ConfigValue.HOST.getConfigPath());
        port = config.getPositiveIntOrThrow(ConfigValue.PORT);
        sslEnabled = config.getBoolean(ConfigValue.SSL_ENABLED.getConfigPath());
        connectionTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.CONNECTION_TIMEOUT);
        readTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.READ_TIMEOUT);
        maxBufferSize = config.getPositiveLongOrThrow(ConfigValue.MAX_BUFFER_SIZE);
        bufferChunkInitialSize = config.getPositiveLongOrThrow(ConfigValue.BUFFER_CHUNK_INITIAL_SIZE);
        bufferChunkRetentionSize = config.getPositiveLongOrThrow(ConfigValue.BUFFER_CHUNK_RETENTION_SIZE);
        bufferChunkRetentionTime =
                config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.BUFFER_CHUNK_RETENTION_TIME);
        flushAttemptInterval = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.FLUSH_ATTEMPT_INTERVAL);
        fileBackupDir = config.getStringOrNull(ConfigValue.FILE_BACKUP_DIR);
        waitUntilBufferFlushed = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.WAIT_UNTIL_BUFFER_FLUSHED);
        waitUntilFlusherTerminated =
                config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.WAIT_UNTIL_FLUSHER_TERMINATED);
        jvmHeapBufferMode = config.getBoolean(ConfigValue.JVM_HEAP_BUFFER_MODE.getConfigPath());
        senderMaxRetryCount = config.getPositiveIntOrThrow(ConfigValue.SENDER_MAX_RETRY_COUNT);
        senderBaseRetryInterval =
                config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.SENDER_BASE_RETRY_INTERVAL);
        senderMaxRetryInterval = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.SENDER_MAX_RETRY_INTERVAL);
        ackResponseMode = config.getBoolean(ConfigValue.ACK_RESPONSE_MODE.getConfigPath());
        waitUntilAllBufferFlushedDurationOnClose =
                config.getDuration(ConfigValue.WAIT_UNTIL_BUFFER_FLUSHED_DURATION_ON_CLOSE.getConfigPath());
    }

    /**
     * Returns {@link FluencyLoggerPublisherConfig}.
     *
     * @param config is supposed to provide the settings of the connection config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static FluencyLoggerPublisherConfig of(final Config config) {
        return new DefaultFluencyLoggerPublisherConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public Fluency buildFluencyLoggerPublisher() {
        final FluencyBuilderForFluentd fluencyBuilderForFluentd = new FluencyBuilderForFluentd();

        fluencyBuilderForFluentd.setSslEnabled(sslEnabled);
        fluencyBuilderForFluentd.setConnectionTimeoutMilli((int) connectionTimeout.toMillis());
        fluencyBuilderForFluentd.setReadTimeoutMilli((int) readTimeout.toMillis());
        fluencyBuilderForFluentd.setMaxBufferSize(maxBufferSize);
        fluencyBuilderForFluentd.setBufferChunkInitialSize((int) bufferChunkInitialSize);
        fluencyBuilderForFluentd.setBufferChunkRetentionSize((int) bufferChunkRetentionSize);
        fluencyBuilderForFluentd.setBufferChunkRetentionTimeMillis((int) bufferChunkRetentionTime.toMillis());
        fluencyBuilderForFluentd.setFlushAttemptIntervalMillis((int) flushAttemptInterval.toMillis());
        fluencyBuilderForFluentd.setWaitUntilBufferFlushed((int) waitUntilBufferFlushed.toSeconds());
        fluencyBuilderForFluentd.setWaitUntilFlusherTerminated((int) waitUntilFlusherTerminated.toSeconds());
        fluencyBuilderForFluentd.setFileBackupDir(fileBackupDir);
        fluencyBuilderForFluentd.setJvmHeapBufferMode(jvmHeapBufferMode);
        fluencyBuilderForFluentd.setSenderMaxRetryCount(senderMaxRetryCount);
        fluencyBuilderForFluentd.setSenderBaseRetryIntervalMillis((int) senderBaseRetryInterval.toMillis());
        fluencyBuilderForFluentd.setSenderMaxRetryIntervalMillis((int) senderMaxRetryInterval.toMillis());
        fluencyBuilderForFluentd.setAckResponseMode(ackResponseMode);

        return fluencyBuilderForFluentd.build(host, port);
    }

    @Override
    public Duration getWaitUntilAllBufferFlushedDurationOnClose() {
        return waitUntilAllBufferFlushedDurationOnClose;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultFluencyLoggerPublisherConfig that = (DefaultFluencyLoggerPublisherConfig) o;
        return port == that.port && sslEnabled == that.sslEnabled && maxBufferSize == that.maxBufferSize &&
                bufferChunkInitialSize == that.bufferChunkInitialSize &&
                bufferChunkRetentionSize == that.bufferChunkRetentionSize &&
                jvmHeapBufferMode == that.jvmHeapBufferMode &&
                senderMaxRetryCount == that.senderMaxRetryCount && ackResponseMode == that.ackResponseMode &&
                Objects.equals(host, that.host) &&
                Objects.equals(connectionTimeout, that.connectionTimeout) &&
                Objects.equals(readTimeout, that.readTimeout) &&
                Objects.equals(bufferChunkRetentionTime, that.bufferChunkRetentionTime) &&
                Objects.equals(flushAttemptInterval, that.flushAttemptInterval) &&
                Objects.equals(fileBackupDir, that.fileBackupDir) &&
                Objects.equals(waitUntilBufferFlushed, that.waitUntilBufferFlushed) &&
                Objects.equals(waitUntilFlusherTerminated, that.waitUntilFlusherTerminated) &&
                Objects.equals(senderBaseRetryInterval, that.senderBaseRetryInterval) &&
                Objects.equals(senderMaxRetryInterval, that.senderMaxRetryInterval) &&
                Objects.equals(waitUntilAllBufferFlushedDurationOnClose, that.waitUntilAllBufferFlushedDurationOnClose);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, sslEnabled, connectionTimeout, readTimeout, maxBufferSize,
                bufferChunkInitialSize,
                bufferChunkRetentionSize, bufferChunkRetentionTime, flushAttemptInterval, fileBackupDir,
                waitUntilBufferFlushed, waitUntilFlusherTerminated, jvmHeapBufferMode, senderMaxRetryCount,
                senderBaseRetryInterval, senderMaxRetryInterval, ackResponseMode,
                waitUntilAllBufferFlushedDurationOnClose);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "host=" + host +
                ", port=" + port +
                ", sslEnabled=" + sslEnabled +
                ", connectionTimeout=" + connectionTimeout +
                ", readTimeout=" + readTimeout +
                ", maxBufferSize=" + maxBufferSize +
                ", bufferChunkInitialSize=" + bufferChunkInitialSize +
                ", bufferChunkRetentionSize=" + bufferChunkRetentionSize +
                ", bufferChunkRetentionTime=" + bufferChunkRetentionTime +
                ", flushAttemptInterval=" + flushAttemptInterval +
                ", fileBackupDir=" + fileBackupDir +
                ", waitUntilBufferFlushed=" + waitUntilBufferFlushed +
                ", waitUntilFlusherTerminated=" + waitUntilFlusherTerminated +
                ", jvmHeapBufferMode=" + jvmHeapBufferMode +
                ", senderMaxRetryCount=" + senderMaxRetryCount +
                ", senderBaseRetryInterval=" + senderBaseRetryInterval +
                ", senderMaxRetryInterval=" + senderMaxRetryInterval +
                ", ackResponseMode=" + ackResponseMode +
                ", waitUntilAllBufferFlushedDurationOnClose=" + waitUntilAllBufferFlushedDurationOnClose +
                "]";
    }

}
