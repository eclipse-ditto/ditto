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

    @Nullable private final String hintNameFilterPidsThatDoesntContainTagInNewestEntry;
    @Nullable private final String hintNameListLatestJournalEntries;
    @Nullable private final String listNewestActiveSnapshotsByBatch;

    private DefaultMongoReadJournalConfig(final ScopedConfig config) {
        hintNameFilterPidsThatDoesntContainTagInNewestEntry = getNullableString(config,
                MongoReadJournalConfigValue.HINT_NAME_FILTER_PIDS_THAT_DOESNT_CONTAIN_TAG_IN_NEWEST_ENTRY);
        hintNameListLatestJournalEntries = getNullableString(config,
                MongoReadJournalConfigValue.HINT_NAME_LIST_LATEST_JOURNAL_ENTRIES);
        listNewestActiveSnapshotsByBatch = getNullableString(config,
                MongoReadJournalConfigValue.HINT_NAME_LIST_NEWEST_ACTIVE_SNAPSHOT_BY_BATCH);
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
        return config.getIsNull(configValue.getConfigPath()) ? null : config.getString(configValue.getConfigPath());
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
    public Optional<String> getIndexNameHintForListNewestActiveSnapshotsByBatch() {
        return Optional.ofNullable(listNewestActiveSnapshotsByBatch);
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
        return Objects.equals(hintNameFilterPidsThatDoesntContainTagInNewestEntry,
                that.hintNameFilterPidsThatDoesntContainTagInNewestEntry) &&
                Objects.equals(hintNameListLatestJournalEntries, that.hintNameListLatestJournalEntries) &&
                Objects.equals(listNewestActiveSnapshotsByBatch, that.listNewestActiveSnapshotsByBatch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hintNameFilterPidsThatDoesntContainTagInNewestEntry, hintNameListLatestJournalEntries,
                listNewestActiveSnapshotsByBatch);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "hintNameFilterPidsThatDoesntContainTagInNewestEntry=" +
                hintNameFilterPidsThatDoesntContainTagInNewestEntry +
                ", hintNameListLatestJournalEntries=" + hintNameListLatestJournalEntries +
                ", listNewestActiveSnapshotsByBatch=" + listNewestActiveSnapshotsByBatch +
                "]";
    }

}
