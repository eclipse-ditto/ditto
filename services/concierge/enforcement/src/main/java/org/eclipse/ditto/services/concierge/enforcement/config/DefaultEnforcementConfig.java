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
package org.eclipse.ditto.services.concierge.enforcement.config;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link EnforcementConfig} for Ditto's Concierge service.
 */
@Immutable
public final class DefaultEnforcementConfig implements EnforcementConfig, Serializable {

    private static final String CONFIG_PATH = "enforcement";

    private static final long serialVersionUID = -3457993946046397252L;

    private final Duration askTimeout;

    private DefaultEnforcementConfig(final ScopedConfig config) {
        askTimeout = config.getDuration(EnforcementConfigValue.ASK_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultEnforcementConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the enforcement config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultEnforcementConfig of(final Config config) {
        return new DefaultEnforcementConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, EnforcementConfigValue.values()));
    }

    @Override
    public Duration getAskTimeout() {
        return askTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultEnforcementConfig that = (DefaultEnforcementConfig) o;
        return askTimeout.equals(that.askTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(askTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "askTimeout=" + askTimeout +
                "]";
    }

}
