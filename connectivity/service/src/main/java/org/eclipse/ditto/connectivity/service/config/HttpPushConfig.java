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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Provides configuration settings of the http-push connection type.
 */
public interface HttpPushConfig {

    /**
     * Create an HTTP Push config object from HOCON.
     *
     * @param config the HOCON.
     * @return the HTTP Push config object.
     */
    static HttpPushConfig of(final Config config) {
        return DefaultHttpPushConfig.of(config);
    }

    /**
     * @return maximum number of messages buffered at the publisher actor before dropping them.
     */
    int getMaxQueueSize();

    /**
     * @return request-timeout of HTTP requests.
     */
    Duration getRequestTimeout();

    /**
     * @return configuration of the proxy for all outgoing HTTP requests.
     */
    HttpProxyConfig getHttpProxyConfig();

    /**
     * @return configuration of HMAC request-signing algorithms.
     */
    Map<String, String> getHmacAlgorithms();

    /**
     * @return configuration for OAuth2.
     */
    OAuth2Config getOAuth2Config();

    /**
     * @return list of http methods for which the request body is omitted.
     */
    List<String> getOmitRequestBodyMethods();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code HttpPushConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * How many messages to buffer in the publisher actor before dropping them. Each takes up to 100 KB heap space.
         */
        MAX_QUEUE_SIZE("max-queue-size", 1000),

        /**
         * Maximum time a request is allowed to wait for a response. If this time is exceeded the HTTP connection will
         * be re-opened.
         */
        REQUEST_TIMEOUT("request-timeout", Duration.ofSeconds(60)),

        /**
         * HMAC request-signing algorithms.
         */
        HMAC_ALGORITHMS("hmac-algorithms", Map.of()),

        /**
         * HTTP methods for which the request body is omitted.
         */
        OMIT_REQUEST_BODY_METHODS("omit-request-body-methods", List.of("GET", "DELETE"));

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
