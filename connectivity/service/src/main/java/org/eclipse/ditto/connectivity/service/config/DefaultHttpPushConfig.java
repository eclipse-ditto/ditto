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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.http.DefaultHttpProxyConfig;
import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link HttpPushConfig}.
 */
@Immutable
final class DefaultHttpPushConfig implements HttpPushConfig {

    private static final String CONFIG_PATH = "http-push";

    private final int maxQueueSize;
    private final Duration requestTimeout;
    private final HttpProxyConfig httpProxyConfig;
    private final Map<String, String> hmacAlgorithms;

    private DefaultHttpPushConfig(final ScopedConfig config) {
        maxQueueSize = config.getInt(ConfigValue.MAX_QUEUE_SIZE.getConfigPath());
        requestTimeout = config.getDuration(ConfigValue.REQUEST_TIMEOUT.getConfigPath());
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new DittoConfigError("Request timeout must be greater than 0");
        }
        httpProxyConfig = DefaultHttpProxyConfig.ofProxy(config);
        hmacAlgorithms = asStringMap(config.getConfig(ConfigValue.HMAC_ALGORITHMS.getConfigPath()));
    }

    static DefaultHttpPushConfig of(final Config config) {
        return new DefaultHttpPushConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    @Override
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    @Override
    public HttpProxyConfig getHttpProxyConfig() {
        return httpProxyConfig;
    }

    @Override
    public Map<String, String> getHmacAlgorithms() {
        return hmacAlgorithms;
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
                Objects.equals(requestTimeout, that.requestTimeout) &&
                Objects.equals(httpProxyConfig, that.httpProxyConfig) &&
                Objects.equals(hmacAlgorithms, that.hmacAlgorithms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxQueueSize, httpProxyConfig, hmacAlgorithms, requestTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxQueueSize=" + maxQueueSize +
                ", requestTimeout=" + requestTimeout +
                ", httpProxyConfig=" + httpProxyConfig +
                ", hmacAlgorithms=" + hmacAlgorithms +
                "]";
    }

    private static Map<String, String> asStringMap(final Config config) {
        try {
            final Map<String, String> map = config.root().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> (String) entry.getValue().unwrapped()));
            return Collections.unmodifiableMap(map);
        } catch (final ClassCastException e) {
            throw new DittoConfigError("In HttpPushConfig, hmac-algorithms must be a map from string to string.");
        }
    }
}
