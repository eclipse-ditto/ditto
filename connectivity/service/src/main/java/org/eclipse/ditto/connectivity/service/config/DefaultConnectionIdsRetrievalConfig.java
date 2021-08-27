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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the config for the retrieval of connection ids.
 */
@Immutable
final class DefaultConnectionIdsRetrievalConfig implements ConnectionIdsRetrievalConfig {

    private static final String CONFIG_PATH = "connection-ids-retrieval";

    private final int readJournalBatchSize;
    private final int readSnapshotBatchSize;

    private DefaultConnectionIdsRetrievalConfig(final ScopedConfig config) {
        readJournalBatchSize =
                config.getPositiveIntOrThrow(ConnectionIdsRetrievalConfigValue.READ_JOURNAL_BATCH_SIZE);
        readSnapshotBatchSize =
                config.getPositiveIntOrThrow(ConnectionIdsRetrievalConfigValue.READ_SNAPSHOT_BATCH_SIZE);
    }

    /**
     * Returns an instance of {@code DefaultReconnectConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultConnectionIdsRetrievalConfig of(final Config config) {
        final var reconnectScopedConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConnectionIdsRetrievalConfigValue.values());

        return new DefaultConnectionIdsRetrievalConfig(reconnectScopedConfig);
    }

    @Override
    public int getReadJournalBatchSize() {
        return readJournalBatchSize;
    }

    @Override
    public int getReadSnapshotBatchSize() {
        return readSnapshotBatchSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultConnectionIdsRetrievalConfig that = (DefaultConnectionIdsRetrievalConfig) o;
        return readJournalBatchSize == that.readJournalBatchSize && readSnapshotBatchSize == that.readSnapshotBatchSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(readJournalBatchSize, readSnapshotBatchSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "readJournalBatchSize=" + readJournalBatchSize +
                ", readSnapshotBatchSize=" + readSnapshotBatchSize +
                "]";
    }

}
