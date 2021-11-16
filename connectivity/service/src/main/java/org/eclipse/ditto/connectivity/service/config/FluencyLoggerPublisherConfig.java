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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.komamitsu.fluency.Fluency;

/**
 * Config for the fluency library used to forward logs to fluentd/fluentbit.
 */
@Immutable
public interface FluencyLoggerPublisherConfig {

    /**
     * Builds a new Fluency instance based on all the configuration provided in this builder.
     *
     * @return the built Fluency instance.
     */
    Fluency buildFluencyLoggerPublisher();

    /**
     * Returns the duration of how long to wait after closing the Fluency buffer.
     * If this is Zero or a negative duration, no waiting for the buffer will be performed.
     *
     * @return the duration of how long to wait after closing the Fluency buffer.
     */
    Duration getWaitUntilAllBufferFlushedDurationOnClose();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code FluencyLoggerPublisherConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The fluentd/fluentbit endpoint hostname.
         */
        HOST("host", "localhost"),

        /**
         * The fluentd/fluentbit endpoint port.
         */
        PORT("port", 24224),

        /**
         * Whether SSL is enabled.
         */
        SSL_ENABLED("sslEnabled", false),

        /**
         * The Socket connection timeout.
         */
        CONNECTION_TIMEOUT("connectionTimeout", Duration.ofSeconds(5)),

        /**
         * The Socket read timeout.
         */
        READ_TIMEOUT("readTimeout", Duration.ofSeconds(5)),

        /**
         * How often to check if a flush was requested from "outside", e.g. by closing the flusher.
         */
        FLUSH_ATTEMPT_INTERVAL("flushAttemptInterval", Duration.ofMillis(600)),

        /**
         * When closing flusher, wait that long for flushing buffer until forcefully stopping flusher.
         */
        WAIT_UNTIL_BUFFER_FLUSHED("waitUntilBufferFlushed", Duration.ofSeconds(60)),

        /**
         * When closing flusher, wait that long for resoruces until forcefully stopping.
         */
        WAIT_UNTIL_FLUSHER_TERMINATED("waitUntilFlusherTerminated", Duration.ofSeconds(60)),

        /**
         * The maximum possible log entries total size to buffer in memory.
         */
        MAX_BUFFER_SIZE("maxBufferSize", 64 * 1024 * 1024),

        /**
         * The initial chunk size, that much memory is allocated at least per instance.
         */
        BUFFER_CHUNK_INITIAL_SIZE("bufferChunkInitialSize", 1024 * 1024),

        /**
         * After the buffer reached this size, perform a flush, sending logs to the endpoint.
         */
        BUFFER_CHUNK_RETENTION_SIZE("bufferChunkRetentionSize", 4 * 1024 * 1024),

        /**
         * After the oldest buffered entry is this old, perform a flush, sending logs to the endpoint.
         */
        BUFFER_CHUNK_RETENTION_TIME("bufferChunkRetentionTime", Duration.ofSeconds(1)),

        /**
         * Use JVM heap memory for buffer pool, if `false`, memory is allocated via `ByteBuffer.allocateDirect`.
         */
        JVM_HEAP_BUFFER_MODE("jvmHeapBufferMode", false),

        /**
         * When set, file backup mode is enabled:
         * "Fluency takes backup of unsent memory buffers as files when closing and then resends them when restarting".
         */
        FILE_BACKUP_DIR("fileBackupDir", null),

        /**
         * How often publishing log entries to the endpoint will be retried (with backoff) if failed.
         */
        SENDER_MAX_RETRY_COUNT("senderMaxRetryCount", 7),

        /**
         * Retry base interval.
         */
        SENDER_BASE_RETRY_INTERVAL("senderBaseRetryInterval", Duration.ofMillis(400)),

        /**
         * Retry max interval.
         */
        SENDER_MAX_RETRY_INTERVAL("senderMaxRetryInterval", Duration.ofSeconds(30)),

        /**
         * Whether ACK response mode is enabled, meaning that it is waited for the ACK of the fluentd/fluentbit
         * instance where the logs are published to. Enables "at-least-once" semantics.
         */
        ACK_RESPONSE_MODE("ackResponseMode", false),

        /**
         * The duration of how long to wait after closing the Fluency buffer.
         * If this is Zero or a negative duration, no waiting for the buffer will be performed.
         */
        WAIT_UNTIL_BUFFER_FLUSHED_DURATION_ON_CLOSE("waitUntilAllBufferFlushedDurationOnClose", Duration.ofSeconds(5)),
        ;

        private final String path;
        @Nullable private final Object defaultValue;

        ConfigValue(final String thePath, @Nullable final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        @Nullable
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}

