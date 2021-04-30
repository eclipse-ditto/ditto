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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's behaviour for retrieval of connection ids.
 */
@Immutable
public interface ConnectionIdsRetrievalConfig {

    /**
     * Returns the number of events to read from the event journal with one query.
     *
     * @return the number of events to read with one query.
     */
    int getReadJournalBatchSize();

    /**
     * Returns the number of entries to read from the snapshot collection with one query.
     *
     * @return the number of entries to read with one query.
     */
    int getReadSnapshotBatchSize();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ReconnectConfig}.
     */
    enum ConnectionIdsRetrievalConfigValue implements KnownConfigValue {

        /**
         * The number of events to read in one query.
         */
        READ_JOURNAL_BATCH_SIZE("read-journal-batch-size", 500),

        /**
         * The number of snapshots to read in one query.
         */
        READ_SNAPSHOT_BATCH_SIZE("read-snapshot-batch-size", 50);

        private final String path;
        private final Object defaultValue;

        // enum constructors are always private.
        ConnectionIdsRetrievalConfigValue(final String thePath, final Object theDefaultValue) {
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
