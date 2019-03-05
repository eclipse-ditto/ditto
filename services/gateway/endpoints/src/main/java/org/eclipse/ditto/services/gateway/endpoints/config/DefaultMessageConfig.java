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
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation for the config of the {@code messages} resource of the Things service.
 */
@Immutable
public final class DefaultMessageConfig implements MessageConfig, Serializable  {

    private static final String CONFIG_PATH = "message";

    private static final long serialVersionUID = -4789137489104250201L;

    private final Duration defaultTimeout;
    private final Duration maxTimeout;

    private DefaultMessageConfig(final ScopedConfig scopedConfig) {
        defaultTimeout = scopedConfig.getDuration(MessageConfigValue.DEFAULT_TIMEOUT.getConfigPath());
        maxTimeout = scopedConfig.getDuration(MessageConfigValue.MAX_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultMessageConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the message config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMessageConfig of(final Config config) {
        return new DefaultMessageConfig(
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
        final DefaultMessageConfig that = (DefaultMessageConfig) o;
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
