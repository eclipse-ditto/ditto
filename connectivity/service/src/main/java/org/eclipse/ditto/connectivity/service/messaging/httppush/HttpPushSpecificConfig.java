/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Class providing access to HTTP push specific configuration.
 */
@Immutable
public final class HttpPushSpecificConfig {

    static final String IDLE_TIMEOUT = "idleTimeout";

    private final Config specificConfig;

    private HttpPushSpecificConfig(final Config specificConfig) {
        this.specificConfig = specificConfig;
    }

    /**
     * Creates a new instance of HttpSpecificConfig based on the {@code specificConfig} of the passed
     * {@code connection}.
     *
     * @param connection the Connection to extract the {@code specificConfig} map from.
     * @param httpConfig the http config to create the default config from.
     * @return the new HttpSpecificConfig instance
     */
    public static HttpPushSpecificConfig fromConnection(final Connection connection, final HttpPushConfig httpConfig) {
        final Map<String, Object> defaultConfig = toDefaultConfig(httpConfig);
        final Config config = ConfigFactory.parseMap(connection.getSpecificConfig())
                .withFallback(ConfigFactory.parseMap(defaultConfig));

        return new HttpPushSpecificConfig(config);
    }

    private static Map<String, Object> toDefaultConfig(final HttpPushConfig httpConfig) {
        final Map<String, Object> defaultMap = new HashMap<>();
        defaultMap.put(IDLE_TIMEOUT, httpConfig.getRequestTimeout());
        return defaultMap;
    }

    /**
     * @return the idle timeout applied for HTTP push requests.
     */
    public Duration idleTimeout() {
        return specificConfig.getDuration(IDLE_TIMEOUT);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpPushSpecificConfig that = (HttpPushSpecificConfig) o;
        return Objects.equals(specificConfig, that.specificConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specificConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "specificConfig=" + specificConfig +
                "]";
    }

}
