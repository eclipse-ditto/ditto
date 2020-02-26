/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.acks.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link AcknowledgementConfig}.
 */
@Immutable
public final class DefaultAcknowledgementConfig implements AcknowledgementConfig {

    private static final String CONFIG_PATH = "acknowledgement";

    private final Duration forwarderFallbackTimeout;

    private DefaultAcknowledgementConfig(final ScopedConfig config) {
        forwarderFallbackTimeout =
                config.getDuration(AcknowledgementConfigValue.FORWARDER_FALLBACK_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultAcknowledgementConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAcknowledgementConfig of(final Config config) {
        return new DefaultAcknowledgementConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, AcknowledgementConfigValue.values()));
    }

    @Override
    public Duration getForwarderFallbackTimeout() {
        return forwarderFallbackTimeout;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAcknowledgementConfig that = (DefaultAcknowledgementConfig) o;
        return forwarderFallbackTimeout == that.forwarderFallbackTimeout;
    }

    @Override
    public int hashCode() {
        return Objects.hash(forwarderFallbackTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "forwarderFallbackTimeout=" + forwarderFallbackTimeout +
                "]";
    }

}
