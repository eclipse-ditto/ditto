/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.connectivity.messaging.config;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link org.eclipse.ditto.services.connectivity.messaging.config.MonitoringCounterConfig}.
 */
@Immutable
public final class DefaultMonitoringCounterConfig implements MonitoringCounterConfig {

    private static final String CONFIG_PATH = "counter";

    /**
     * Returns {@link org.eclipse.ditto.services.connectivity.messaging.config.MonitoringCounterConfig}.
     *
     * @param config is supposed to provide the settings of the connection config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static MonitoringCounterConfig of(final Config config) {
        return new DefaultMonitoringCounterConfig();
    }

    @Override
    public String toString() {
        return "DefaultMonitoringCounterConfig{}";
    }

}
