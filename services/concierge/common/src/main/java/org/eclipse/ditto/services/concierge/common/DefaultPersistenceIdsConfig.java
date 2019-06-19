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
package org.eclipse.ditto.services.concierge.common;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

@Immutable
final class DefaultPersistenceIdsConfig implements PersistenceIdsConfig {

    private static final String CONFIG_PATH = "persistence-ids";

    private final int burst;
    private final Duration streamRequestTimeout;
    private final Duration streamIdleTimeout;

    private DefaultPersistenceIdsConfig(final Config config) {
        burst = config.getInt(ConfigValue.BURST.getConfigPath());
        streamRequestTimeout = config.getDuration(ConfigValue.STREAM_REQUEST_TIMEOUT.getConfigPath());
        streamIdleTimeout = config.getDuration(ConfigValue.STREAM_IDLE_TIMEOUT.getConfigPath());
    }

    static PersistenceIdsConfig of(final Config config) {
        return new DefaultPersistenceIdsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public int getBurst() {
        return burst;
    }

    @Override
    public Duration getStreamRequestTimeout() {
        return streamRequestTimeout;
    }

    @Override
    public Duration getStreamIdleTimeout() {
        return streamIdleTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof DefaultPersistenceIdsConfig) {
            final DefaultPersistenceIdsConfig that = (DefaultPersistenceIdsConfig) o;
            return burst == that.burst &&
                    Objects.equals(streamRequestTimeout, that.streamRequestTimeout) &&
                    Objects.equals(streamIdleTimeout, that.streamIdleTimeout);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(burst, streamRequestTimeout, streamIdleTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[ burst=" + burst +
                ", streamRequestTimeout" + streamRequestTimeout +
                ", streamIdleTimeout" + streamIdleTimeout +
                "]";
    }
}
