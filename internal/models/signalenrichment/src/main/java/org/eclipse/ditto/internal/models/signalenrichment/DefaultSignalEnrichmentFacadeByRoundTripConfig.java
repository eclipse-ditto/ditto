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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link SignalEnrichmentFacadeByRoundTripConfig}.
 */
@Immutable
public final class DefaultSignalEnrichmentFacadeByRoundTripConfig implements SignalEnrichmentFacadeByRoundTripConfig {

    private final Duration askTimeout;

    private DefaultSignalEnrichmentFacadeByRoundTripConfig(final ConfigWithFallback configWithFallback) {
        this.askTimeout = configWithFallback.getDuration(
                SignalEnrichmentFacadeByRoundTripConfigValue.ASK_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultConnectionEnrichmentConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the provider specific config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSignalEnrichmentFacadeByRoundTripConfig of(final Config config) {
        return new DefaultSignalEnrichmentFacadeByRoundTripConfig(ConfigWithFallback.newInstance(config,
                SignalEnrichmentFacadeByRoundTripConfigValue.values()));
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
        final DefaultSignalEnrichmentFacadeByRoundTripConfig that = (DefaultSignalEnrichmentFacadeByRoundTripConfig) o;
        return Objects.equals(askTimeout, that.askTimeout);
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
