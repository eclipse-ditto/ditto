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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Configuration of the PostgreSQL search {@code delete_at} reaper (plan §3.6) — the in-service replacement for Mongo's
 * TTL index on {@code deleteAt}. Lives under the HOCON namespace {@code ditto.postgresql.search.reaper.*}.
 */
@Immutable
public interface PostgresDeleteAtReaperConfig {

    /**
     * @return how often the reaper attempts a tick (acquire the advisory lock, drain, release).
     */
    Duration getInterval();

    /**
     * The number of rows deleted per batch — the {@code LIMIT} of the mandated {@code SKIP LOCKED} subselect. Keeps a
     * single {@code DELETE} statement's lock footprint bounded so a reaper batch never stalls behind (or blocks) an
     * in-flight {@code ThingUpdater} transaction.
     *
     * @return the configured batch size.
     */
    int getBatchSize();

    /**
     * The safety valve: at most this many batches run within ONE tick. Without it, a huge backlog (e.g. after a large
     * namespace purge) would keep one tick's transaction open indefinitely; a backlog larger than
     * {@code batchSize * maxBatchesPerTick} is instead finished across subsequent ticks.
     *
     * @return the configured maximum number of batches per tick.
     */
    int getMaxBatchesPerTick();

    /**
     * An enumeration of the known config path / default value pairs of {@code PostgresDeleteAtReaperConfig}.
     */
    enum PostgresDeleteAtReaperConfigValue implements KnownConfigValue {

        INTERVAL("interval", Duration.ofSeconds(30L)),

        BATCH_SIZE("batch-size", 1_000),

        MAX_BATCHES_PER_TICK("max-batches-per-tick", 50);

        private final String path;
        private final Object defaultValue;

        PostgresDeleteAtReaperConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
