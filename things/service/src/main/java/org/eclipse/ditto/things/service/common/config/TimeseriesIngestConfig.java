/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration of the ingest of WoT-annotated properties into the timeseries service.
 */
@Immutable
public interface TimeseriesIngestConfig {

    /**
     * @return how far before its event a data point's measurement time may lie; an older time is replaced by the
     * event's time.
     */
    Duration getMaxMeasurementAge();

    /**
     * @return attempts per batch of data points, including the first.
     */
    int getRetryMaxAttempts();

    /**
     * @return delay before the second attempt, doubled for every further one up to {@link #getRetryMaxBackoff()}.
     */
    Duration getRetryBackoff();

    /**
     * @return upper bound of a single retry delay.
     */
    Duration getRetryMaxBackoff();

    /**
     * @return batches allowed to wait for a retry at once per things instance; a batch failing beyond it is dropped.
     */
    int getRetryMaxPendingRetries();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code TimeseriesIngestConfig}.
     */
    enum TimeseriesIngestConfigValue implements KnownConfigValue {

        MAX_MEASUREMENT_AGE("max-measurement-age", Duration.ofDays(35)),

        RETRY_MAX_ATTEMPTS("ingest-retry.max-attempts", 10),

        RETRY_BACKOFF("ingest-retry.backoff", Duration.ofMillis(500)),

        RETRY_MAX_BACKOFF("ingest-retry.max-backoff", Duration.ofSeconds(30)),

        RETRY_MAX_PENDING_RETRIES("ingest-retry.max-pending-retries", 20_000);

        private final String path;
        private final Object defaultValue;

        TimeseriesIngestConfigValue(final String thePath, final Object theDefaultValue) {
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
