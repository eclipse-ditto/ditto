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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.http.DefaultHttpProxyConfig;
import org.eclipse.ditto.services.base.config.http.HttpProxyConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link HttpPushConfig}.
 */
@Immutable
final class DefaultHttpPushConfig implements HttpPushConfig {

    private static final String CONFIG_PATH = "http-push";

    private final int maxQueueSize;
    private final HttpProxyConfig httpProxyConfig;

    private DefaultHttpPushConfig(final ScopedConfig config) {
        maxQueueSize = config.getInt(ConfigValue.MAX_QUEUE_SIZE.getConfigPath());
        httpProxyConfig = DefaultHttpProxyConfig.ofProxy(config);
    }

    static DefaultHttpPushConfig of(final Config config) {
        return new DefaultHttpPushConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    @Override
    public HttpProxyConfig getHttpProxyConfig() {
        return httpProxyConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHttpPushConfig that = (DefaultHttpPushConfig) o;
        return maxQueueSize == that.maxQueueSize &&
                Objects.equals(httpProxyConfig, that.httpProxyConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxQueueSize, httpProxyConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxQueueSize=" + maxQueueSize +
                ", httpProxyConfig=" + httpProxyConfig +
                "]";
    }
}
