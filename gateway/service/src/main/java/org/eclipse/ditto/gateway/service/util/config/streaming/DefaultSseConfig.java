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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the SSE config.
 */
@Immutable
final class DefaultSseConfig implements SseConfig {

    private final ThrottlingConfig throttlingConfig;
    private final int publisherBackpressureBufferSize;

    private DefaultSseConfig(final ScopedConfig scopedConfig) {
        throttlingConfig = ThrottlingConfig.of(scopedConfig);
        publisherBackpressureBufferSize =
                scopedConfig.getPositiveIntOrThrow(SseConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE);
    }

    /**
     * Returns an instance of {@code SseConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the SSE socket config at "sse".
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static SseConfig of(final Config config) {
        return new DefaultSseConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, SseConfig.SseConfigValue.values()));
    }

    @Override
    public ThrottlingConfig getThrottlingConfig() {
        return throttlingConfig;
    }

    @Override
    public int getPublisherBackpressureBufferSize() {
        return publisherBackpressureBufferSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultSseConfig that = (DefaultSseConfig) o;
        return publisherBackpressureBufferSize == that.publisherBackpressureBufferSize &&
                Objects.equals(throttlingConfig, that.throttlingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(throttlingConfig, publisherBackpressureBufferSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "throttlingConfig=" + throttlingConfig +
                ", publisherBackpressureBufferSize=" + publisherBackpressureBufferSize +
                "]";
    }

}
