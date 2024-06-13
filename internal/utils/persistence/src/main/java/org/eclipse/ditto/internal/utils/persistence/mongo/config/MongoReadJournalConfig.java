/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the {@code MongoReadJournal}.
 */
@Immutable
public interface MongoReadJournalConfig {

    /**
     * @return whether additional index for "pid" + "_id" should be created in order to speed up MongoReadJournal
     * aggregation queries on the snapshot collection.
     */
    boolean shouldCreateAdditionalSnapshotAggregationIndexPidId();

    /**
     * @return whether additional index for "pid" + "sn" should be created in order to speed up MongoReadJournal
     * aggregation queries on the snapshot collection.
     */
    boolean shouldCreateAdditionalSnapshotAggregationIndexPidSn();

    /**
     * @return whether additional index for "pid" + "sn" + "_id" should be created in order to speed up MongoReadJournal
     * aggregation queries on the snapshot collection.
     */
    boolean shouldCreateAdditionalSnapshotAggregationIndexPidSnId();

    /**
     * @return the optional hint name for aggregation done in {@code filterPidsThatDoesntContainTagInNewestEntry}.
     */
    Optional<String> getIndexNameHintForFilterPidsThatDoesntContainTagInNewestEntry();

    /**
     * @return the optional hint name for aggregation done in {@code listLatestJournalEntries}.
     */
    Optional<String> getIndexNameHintForListLatestJournalEntries();

    /**
     * @return the optional hint name for aggregation done in {@code listNewestActiveSnapshotsByBatch} containing both
     * "pid" and "_id" fields in first "$match".
     */
    Optional<String> getIndexNameHintForListNewestActiveSnapshotsByBatchPidId();

    /**
     * @return the optional hint name for aggregation done in {@code listNewestActiveSnapshotsByBatch} only containing
     * "pid" field in first "$match".
     */
    Optional<String> getIndexNameHintForListNewestActiveSnapshotsByBatchPid();

    /**
     * @return the optional hint name for aggregation done in {@code listNewestActiveSnapshotsByBatch} only containing
     * "_id" field in first "$match".
     */
    Optional<String> getIndexNameHintForListNewestActiveSnapshotsByBatchId();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MongoReadJournalConfig}.
     */
    enum MongoReadJournalConfigValue implements KnownConfigValue {

        /**
         * Whether additional index for "pid" + "_id" should be created in order to speed up MongoReadJournal
         * aggregation queries on the snapshot collection.
         */
        SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_ID("should-create-additional-snapshot-aggregation-index-pid-id", false),

        /**
         * Whether additional index for "pid" + "sn" should be created in order to speed up MongoReadJournal aggregation
         * queries on the snapshot collection.
         */
        SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_SN("should-create-additional-snapshot-aggregation-index-pid-sn", false),

        /**
         * Whether additional index for "pid" + "sn" + "_id" should be created in order to speed up MongoReadJournal aggregation
         * queries on the snapshot collection.
         */
        SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_SN_ID("should-create-additional-snapshot-aggregation-index-pid-sn-id", false),

        /**
         * Hint name for aggregation done in {@code filterPidsThatDoesntContainTagInNewestEntry}.
         */
        HINT_NAME_FILTER_PIDS_THAT_DOESNT_CONTAIN_TAG_IN_NEWEST_ENTRY("hint-name-filterPidsThatDoesntContainTagInNewestEntry", null),

        /**
         * Hint name for aggregation done in {@code listLatestJournalEntries}.
         */
        HINT_NAME_LIST_LATEST_JOURNAL_ENTRIES("hint-name-listLatestJournalEntries", null),

        /**
         * Hint name for aggregation done in {@code listNewestActiveSnapshotsByBatchPidId}.
         */
        HINT_NAME_LIST_NEWEST_ACTIVE_SNAPSHOT_BY_BATCH_PID_ID("hint-name-listNewestActiveSnapshotsByBatchPidId", null),

        /**
         * Hint name for aggregation done in {@code listNewestActiveSnapshotsByBatchPid}.
         */
        HINT_NAME_LIST_NEWEST_ACTIVE_SNAPSHOT_BY_BATCH_PID("hint-name-listNewestActiveSnapshotsByBatchPid", null),

        /**
         * Hint name for aggregation done in {@code listNewestActiveSnapshotsByBatchId}.
         */
        HINT_NAME_LIST_NEWEST_ACTIVE_SNAPSHOT_BY_BATCH_ID("hint-name-listNewestActiveSnapshotsByBatchId", null);

        private final String path;
        private final Object defaultValue;

        MongoReadJournalConfigValue(final String thePath, @Nullable final Object theDefaultValue) {
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
