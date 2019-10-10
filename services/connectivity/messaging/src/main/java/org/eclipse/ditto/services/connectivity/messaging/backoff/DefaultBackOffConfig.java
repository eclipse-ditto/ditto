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
package org.eclipse.ditto.services.connectivity.messaging.backoff;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link BackOffConfig}.
 */
@Immutable
public final class DefaultBackOffConfig implements BackOffConfig {

    private static final String CONFIG_PATH = "backoff";

    private final TimeoutConfig timeoutConfig;
    private final Duration askTimeout;

    private DefaultBackOffConfig(final ScopedConfig config) {
        timeoutConfig = DefaultTimeoutConfig.of(config);
        askTimeout = config.getDuration(BackOffConfigValue.ASK_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultBackOffConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultBackOffConfig of(final Config config) {
        return new DefaultBackOffConfig(DefaultScopedConfig.newInstance(config, CONFIG_PATH));
    }

    @Override
    public TimeoutConfig getTimeoutConfig() {
        return timeoutConfig;
    }

    @Override
    public Duration getAskTimeout() {
        return askTimeout;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "timeoutConfig=" + timeoutConfig +
                ", askTimeout=" + askTimeout +
                "]";
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultBackOffConfig that = (DefaultBackOffConfig) o;
        return Objects.equals(timeoutConfig, that.timeoutConfig) &&
                Objects.equals(askTimeout, that.askTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeoutConfig, askTimeout);
    }

}
