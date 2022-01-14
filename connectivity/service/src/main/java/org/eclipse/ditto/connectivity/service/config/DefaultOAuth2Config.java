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
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link OAuth2Config}.
 */
@Immutable
final class DefaultOAuth2Config implements OAuth2Config {

    private static final String CONFIG_PATH = "oauth2";
    private static final String TOKEN_CACHE_CONFIG_PATH = "token-cache";

    private final Duration maxClockSkew;
    private final boolean enforceHttps;
    private final CacheConfig tokenCacheConfig;

    private DefaultOAuth2Config(final ScopedConfig config) {
        maxClockSkew = config.getDuration(ConfigValue.MAX_CLOCK_SKEW.getConfigPath());
        enforceHttps = config.getBoolean(ConfigValue.ENFORCE_HTTPS.getConfigPath());
        tokenCacheConfig = DefaultCacheConfig.of(config, TOKEN_CACHE_CONFIG_PATH);
    }

    static DefaultOAuth2Config of(final Config config) {
        return new DefaultOAuth2Config(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public Duration getMaxClockSkew() {
        return maxClockSkew;
    }

    @Override
    public boolean shouldEnforceHttps() {
        return enforceHttps;
    }

    @Override
    public CacheConfig getTokenCacheConfig() {
        return tokenCacheConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultOAuth2Config that = (DefaultOAuth2Config) o;
        return Objects.equals(maxClockSkew, that.maxClockSkew) &&
                Objects.equals(tokenCacheConfig, that.tokenCacheConfig) && enforceHttps == that.enforceHttps;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforceHttps, maxClockSkew, tokenCacheConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "requestTimeout=" + maxClockSkew +
                ", httpProxyConfig=" + enforceHttps +
                ", tokenCacheConfig=" + tokenCacheConfig +
                "]";
    }

}
