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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements the config for the handling of event journal entries.
 */
@Immutable
public final class DefaultMongoReadJournalConfig implements MongoReadJournalConfig {

    private static final String CONFIG_PATH = "read-journal";

    private final boolean createAdditionalSnapshotAggregationIndexPidId;
    private final boolean createAdditionalSnapshotAggregationIndexPidSn;
    private final boolean createAdditionalSnapshotAggregationIndexPidSnId;
    @Nullable private final String hintNameFilterPidsThatDoesntContainTagInNewestEntry;
    @Nullable private final String hintNameListLatestJournalEntries;
    @Nullable private final String listNewestActiveSnapshotsByBatchPidId;
    @Nullable private final String listNewestActiveSnapshotsByBatchPid;
    @Nullable private final String listNewestActiveSnapshotsByBatchId;

    private DefaultMongoReadJournalConfig(final ScopedConfig config) {
        createAdditionalSnapshotAggregationIndexPidId = config.getBoolean(
                MongoReadJournalConfigValue.SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_ID.getConfigPath()
        );
        createAdditionalSnapshotAggregationIndexPidSn = config.getBoolean(
                MongoReadJournalConfigValue.SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_SN.getConfigPath()
        );
        createAdditionalSnapshotAggregationIndexPidSnId = config.getBoolean(
                MongoReadJournalConfigValue.SHOULD_CREATE_ADDITIONAL_SNAPSHOT_AGGREGATION_INDEX_PID_SN_ID.getConfigPath()
        );
        hintNameFilterPidsThatDoesntContainTagInNewestEntry = getNullableString(config,
                MongoReadJournalConfigValue.HINT_NAME_FILTER_PIDS_THAT_DOESNT_CONTAIN_TAG_IN_NEWEST_ENTRY);
        hintNameListLatestJournalEntries = getNullableString(config,
                MongoReadJournalConfigValue.HINT_NAME_LIST_LATEST_JOURNAL_ENTRIES);
        listNewestActiveSnapshotsByBatchPidId = getNullableString(config,
                MongoReadJournalConfigValue.HINT_NAME_LIST_NEWEST_ACTIVE_SNAPSHOT_BY_BATCH_PID_ID);
        listNewestActiveSnapshotsByBatchPid = getNullableString(config,
                MongoReadJournalConfigValue.HINT_NAME_LIST_NEWEST_ACTIVE_SNAPSHOT_BY_BATCH_PID);
        listNewestActiveSnapshotsByBatchId = getNullableString(config,
                MongoReadJournalConfigValue.HINT_NAME_LIST_NEWEST_ACTIVE_SNAPSHOT_BY_BATCH_ID);
    }

    /**
     * Returns an instance of the default mongo read journal config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the mongo read journal config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMongoReadJournalConfig of(final Config config) {
        return new DefaultMongoReadJournalConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, MongoReadJournalConfigValue.values()));
    }

    @Nullable
    private static String getNullableString(final Config config, final KnownConfigValue configValue) {
        return config.getIsNull(configValue.getConfigPath()) ? null :
                Optional.of(config.getString(configValue.getConfigPath()))
                        .filter(s -> !s.equals("null"))
                        .orElse(null);
    }

    @Override
    public boolean shouldCreateAdditionalSnapshotAggregationIndexPidId() {
        return createAdditionalSnapshotAggregationIndexPidId;
    }

    @Override
    public boolean shouldCreateAdditionalSnapshotAggregationIndexPidSn() {
        return createAdditionalSnapshotAggregationIndexPidSn;
    }

    @Override
    public boolean shouldCreateAdditionalSnapshotAggregationIndexPidSnId() {
        return createAdditionalSnapshotAggregationIndexPidSnId;
    }

    @Override
    public Optional<String> getIndexNameHintForFilterPidsThatDoesntContainTagInNewestEntry() {
        return Optional.ofNullable(hintNameFilterPidsThatDoesntContainTagInNewestEntry);
    }

    @Override
    public Optional<String> getIndexNameHintForListLatestJournalEntries() {
        return Optional.ofNullable(hintNameListLatestJournalEntries);
    }

    @Override
    public Optional<String> getIndexNameHintForListNewestActiveSnapshotsByBatchPidId() {
        return Optional.ofNullable(listNewestActiveSnapshotsByBatchPidId);
    }

    @Override
    public Optional<String> getIndexNameHintForListNewestActiveSnapshotsByBatchPid() {
        return Optional.ofNullable(listNewestActiveSnapshotsByBatchPid);
    }

    @Override
    public Optional<String> getIndexNameHintForListNewestActiveSnapshotsByBatchId() {
        return Optional.ofNullable(listNewestActiveSnapshotsByBatchId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMongoReadJournalConfig that = (DefaultMongoReadJournalConfig) o;
        return createAdditionalSnapshotAggregationIndexPidId == that.createAdditionalSnapshotAggregationIndexPidId &&
                createAdditionalSnapshotAggregationIndexPidSn == that.createAdditionalSnapshotAggregationIndexPidSn &&
                createAdditionalSnapshotAggregationIndexPidSnId ==
                        that.createAdditionalSnapshotAggregationIndexPidSnId &&
                Objects.equals(hintNameFilterPidsThatDoesntContainTagInNewestEntry,
                        that.hintNameFilterPidsThatDoesntContainTagInNewestEntry) &&
                Objects.equals(hintNameListLatestJournalEntries, that.hintNameListLatestJournalEntries) &&
                Objects.equals(listNewestActiveSnapshotsByBatchPidId, that.listNewestActiveSnapshotsByBatchPidId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(createAdditionalSnapshotAggregationIndexPidId,
                createAdditionalSnapshotAggregationIndexPidSn, createAdditionalSnapshotAggregationIndexPidSnId,
                hintNameFilterPidsThatDoesntContainTagInNewestEntry, hintNameListLatestJournalEntries,
                listNewestActiveSnapshotsByBatchPidId, listNewestActiveSnapshotsByBatchPid,
                listNewestActiveSnapshotsByBatchId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "createAdditionalSnapshotAggregationIndexPidId=" + createAdditionalSnapshotAggregationIndexPidId +
                ", createAdditionalSnapshotAggregationIndexPidSn=" + createAdditionalSnapshotAggregationIndexPidSn +
                ", createAdditionalSnapshotAggregationIndexPidSnId=" + createAdditionalSnapshotAggregationIndexPidSnId +
                ", hintNameFilterPidsThatDoesntContainTagInNewestEntry=" +
                hintNameFilterPidsThatDoesntContainTagInNewestEntry +
                ", hintNameListLatestJournalEntries=" + hintNameListLatestJournalEntries +
                ", listNewestActiveSnapshotsByBatchPidId=" + listNewestActiveSnapshotsByBatchPidId +
                ", listNewestActiveSnapshotsByBatchPid=" + listNewestActiveSnapshotsByBatchPid +
                ", listNewestActiveSnapshotsByBatchId=" + listNewestActiveSnapshotsByBatchId +
                "]";
    }

}
