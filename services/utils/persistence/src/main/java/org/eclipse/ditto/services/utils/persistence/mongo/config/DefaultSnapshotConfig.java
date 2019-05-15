/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *  *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements the config for the handling of snapshots of policy entities.
 */
@Immutable
public final class DefaultSnapshotConfig implements SnapshotConfig, Serializable {

    private static final String CONFIG_PATH = "snapshot";

    private static final long serialVersionUID = -8982980480384377972L;

    private final Duration interval;
    private final long threshold;
    private final boolean deleteOldSnapshot;
    private final boolean deleteOldEvents;

    private DefaultSnapshotConfig(final ScopedConfig config) {
        interval = config.getDuration(SnapshotConfigValue.INTERVAL.getConfigPath());
        threshold = getThreshold(config);
        deleteOldSnapshot = config.getBoolean(SnapshotConfigValue.DELETE_OLD_SNAPSHOT.getConfigPath());
        deleteOldEvents = config.getBoolean(SnapshotConfigValue.DELETE_OLD_EVENTS.getConfigPath());
    }

    private static long getThreshold(final ScopedConfig config) {
        final long result = config.getLong(SnapshotConfigValue.THRESHOLD.getConfigPath());
        if (1 > result) {
            final String msgPattern = "The snapshot threshold must be positive but it was <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, result));
        }
        return result;
    }

    /**
     * Returns an instance of the default snapshot config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the snapshot config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSnapshotConfig of(final Config config) {
        return new DefaultSnapshotConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, SnapshotConfigValue.values()));
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public long getThreshold() {
        return threshold;
    }

    @Override
    public boolean isDeleteOldSnapshot() {
        return deleteOldSnapshot;
    }

    @Override
    public boolean isDeleteOldEvents() {
        return deleteOldEvents;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSnapshotConfig that = (DefaultSnapshotConfig) o;
        return threshold == that.threshold &&
                deleteOldSnapshot == that.deleteOldSnapshot &&
                deleteOldEvents == that.deleteOldEvents &&
                Objects.equals(interval, that.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(interval, threshold, deleteOldSnapshot, deleteOldEvents);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "interval=" + interval +
                ", threshold=" + threshold +
                ", deleteOldSnapshot=" + deleteOldSnapshot +
                ", deleteOldEvents=" + deleteOldEvents +
                "]";
    }

}
