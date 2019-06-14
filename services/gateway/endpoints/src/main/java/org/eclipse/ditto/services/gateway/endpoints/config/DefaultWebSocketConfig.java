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
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the web socket config.
 */
@Immutable
public final class DefaultWebSocketConfig implements WebSocketConfig {

    private static final String CONFIG_PATH = "websocket";

    private final int subscriberBackpressureQueueSize;
    private final int publisherBackpressureBufferSize;

    private DefaultWebSocketConfig(final ScopedConfig scopedConfig) {
        subscriberBackpressureQueueSize =
                scopedConfig.getInt(WebSocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getConfigPath());
        publisherBackpressureBufferSize =
                scopedConfig.getInt(WebSocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultWebSocketConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the web socket config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultWebSocketConfig of(final Config config) {
        return new DefaultWebSocketConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, WebSocketConfigValue.values()));
    }

    @Override
    public int getSubscriberBackpressureQueueSize() {
        return subscriberBackpressureQueueSize;
    }

    @Override
    public int getPublisherBackpressureBufferSize() {
        return publisherBackpressureBufferSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultWebSocketConfig that = (DefaultWebSocketConfig) o;
        return subscriberBackpressureQueueSize == that.subscriberBackpressureQueueSize &&
                publisherBackpressureBufferSize == that.publisherBackpressureBufferSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriberBackpressureQueueSize, publisherBackpressureBufferSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "subscriberBackpressureQueueSize=" + subscriberBackpressureQueueSize +
                ", publisherBackpressureBufferSize=" + publisherBackpressureBufferSize +
                "]";
    }

}
