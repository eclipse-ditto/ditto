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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements the config for the handling of snapshots of entities.
 */
@Immutable
public final class DefaultSnapshotConfig implements SnapshotConfig {

    private static final String CONFIG_PATH = "snapshot";

    private final Duration interval;
    private final long threshold;

    private DefaultSnapshotConfig(final ScopedConfig config) {
        interval = config.getNonNegativeAndNonZeroDurationOrThrow(SnapshotConfigValue.INTERVAL);
        threshold = config.getPositiveLongOrThrow((SnapshotConfigValue.THRESHOLD));
    }

    /**
     * Returns an instance of the default snapshot config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the snapshot config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSnapshotConfig that = (DefaultSnapshotConfig) o;
        return threshold == that.threshold && Objects.equals(interval, that.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(interval, threshold);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "interval=" + interval +
                ", threshold=" + threshold +
                "]";
    }

}
