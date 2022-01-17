/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Provides configuration settings of the http-push connection for OAuth2.
 */
public interface OAuth2Config {

    /**
     * Create an {@code OAuth2Config} object.
     *
     * @param config the HOCON.
     * @return the OAuth2Config object.
     */
    static OAuth2Config of(final Config config) {
        return DefaultOAuth2Config.of(config);
    }

    /**
     * @return Maximum expected clock skew. Tokens are renewed before they expire this much time into the future.
     */
    Duration getMaxClockSkew();

    /**
     * @return configuration for OAuth2.
     */
    CacheConfig getTokenCacheConfig();

    /**
     * @return Whether HTTPS is the required protocol of the token endpoint. Should be true in a production
     * environment to avoid transmitting client secret in plain text.
     */
    boolean shouldEnforceHttps();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code OAuthConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Maximum expected clock skew.
         */
        MAX_CLOCK_SKEW("max-clock-skew", Duration.ofMinutes(1L)),

        /**
         * Whether HTTPS is the required protocol of the token endpoint.
         */
        ENFORCE_HTTPS("enforce-https", true);

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
