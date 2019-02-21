/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the snapshotting behaviour config.
 */
@Immutable
public final class DefaultSnapshotConfig implements ConnectionConfig.SnapshotConfig, Serializable {

    private static final String CONFIG_PATH = "snapshot";

    private static final long serialVersionUID = 3678342704983434382L;

    private final int threshold;

    private DefaultSnapshotConfig(final ScopedConfig config) {
        threshold = config.getInt(SnapshotConfigValue.THRESHOLD.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultSnapshotConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSnapshotConfig of(final Config config) {
        return new DefaultSnapshotConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, SnapshotConfigValue.values()));
    }

    @Override
    public int getThreshold() {
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
        return Objects.equals(threshold, that.threshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threshold);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "threshold=" + threshold +
                "]";
    }

}
