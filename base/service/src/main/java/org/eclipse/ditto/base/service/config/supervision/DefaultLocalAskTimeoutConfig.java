/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.base.service.config.supervision;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the local ACK timeout config.
 */
@Immutable
public class DefaultLocalAskTimeoutConfig implements LocalAskTimeoutConfig {

    private static final String CONFIG_PATH = "local-ask";
    private final Duration askTimeout;

    private DefaultLocalAskTimeoutConfig(final ScopedConfig config) {
        askTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(LocalAskTimeoutConfigValue.ASK_TIMEOUT);
    }

    /**
     * Returns an instance of {@code DefaultLocalAskTimeoutConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the local ASK timeout config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultLocalAskTimeoutConfig of(final Config config) {
        return new DefaultLocalAskTimeoutConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                LocalAskTimeoutConfig.LocalAskTimeoutConfigValue.values()));
    }

    @Override
    public Duration getLocalAckTimeout() {
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
        final DefaultLocalAskTimeoutConfig that = (DefaultLocalAskTimeoutConfig) o;
        return Objects.equals(askTimeout, that.askTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(askTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "askTimeout=" + askTimeout +
                ']';
    }
}
