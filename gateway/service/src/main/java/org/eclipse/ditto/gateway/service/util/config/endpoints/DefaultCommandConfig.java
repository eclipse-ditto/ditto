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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link CommandConfig}.
 *
 * @since 1.1.0
 */
@Immutable
public final class DefaultCommandConfig implements CommandConfig {

    private static final String CONFIG_PATH = "command";

    private final Duration defaultTimeout;
    private final Duration maxTimeout;
    private final Duration smartChannelBuffer;

    private DefaultCommandConfig(final ScopedConfig scopedConfig) {
        defaultTimeout = scopedConfig.getNonNegativeAndNonZeroDurationOrThrow(CommandConfigValue.DEFAULT_TIMEOUT);
        maxTimeout = scopedConfig.getNonNegativeAndNonZeroDurationOrThrow(CommandConfigValue.MAX_TIMEOUT);
        smartChannelBuffer =
                scopedConfig.getNonNegativeAndNonZeroDurationOrThrow(CommandConfigValue.SMART_CHANNEL_BUFFER);
    }

    /**
     * Returns an instance of {@code DefaultCommandConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the message config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCommandConfig of(final Config config) {
        return new DefaultCommandConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, CommandConfigValue.values()));
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
    public Duration getSmartChannelBuffer() {
        return smartChannelBuffer;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCommandConfig that = (DefaultCommandConfig) o;
        return Objects.equals(defaultTimeout, that.defaultTimeout) &&
                Objects.equals(maxTimeout, that.maxTimeout) &&
                Objects.equals(smartChannelBuffer, that.smartChannelBuffer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultTimeout, maxTimeout, smartChannelBuffer);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "defaultTimeout=" + defaultTimeout +
                ", maxTimeout=" + maxTimeout +
                ", smartChannelBuffer=" + smartChannelBuffer +
                "]";
    }

}
