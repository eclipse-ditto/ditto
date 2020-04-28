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
package org.eclipse.ditto.services.gateway.util.config.endpoints;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation for the config of the {@code inbox/claim} resource of the gateway.
 */
@Immutable
public final class DefaultClaimMessageConfig implements MessageConfig {

    private static final String CONFIG_PATH = "claim-message";

    private final Duration defaultTimeout;
    private final Duration maxTimeout;

    private DefaultClaimMessageConfig(final ScopedConfig scopedConfig) {
        defaultTimeout = scopedConfig.getDuration(MessageConfigValue.DEFAULT_TIMEOUT.getConfigPath());
        maxTimeout = scopedConfig.getDuration(MessageConfigValue.MAX_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultClaimMessageConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the claim-message config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultClaimMessageConfig of(final Config config) {
        return new DefaultClaimMessageConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, MessageConfigValue.values()));
    }

    @Override
    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    @Override
    public Duration getMaxTimeout() {
        return maxTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultClaimMessageConfig that = (DefaultClaimMessageConfig) o;
        return Objects.equals(defaultTimeout, that.defaultTimeout) && Objects.equals(maxTimeout, that.maxTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultTimeout, maxTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "defaultTimeout=" + defaultTimeout +
                ", maxTimeout=" + maxTimeout +
                "]";
    }

}
