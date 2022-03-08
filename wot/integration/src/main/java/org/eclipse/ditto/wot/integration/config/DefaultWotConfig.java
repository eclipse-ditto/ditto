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
package org.eclipse.ditto.wot.integration.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.http.DefaultHttpProxyConfig;
import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the WoT (Web of Things) config.
 *
 * @since 2.4.0
 */
@Immutable
public final class DefaultWotConfig implements WotConfig {

    private static final String CONFIG_PATH = "wot";

    private final HttpProxyConfig httpProxyConfig;
    private final CacheConfig cacheConfig;
    private final ToThingDescriptionConfig toThingDescriptionConfig;
    private final DefaultTmBasedCreationConfig tmBasedCreationConfig;

    private DefaultWotConfig(final ScopedConfig scopedConfig) {
        httpProxyConfig = DefaultHttpProxyConfig.ofHttpProxy(scopedConfig);
        cacheConfig = DefaultCacheConfig.of(scopedConfig, "cache");
        toThingDescriptionConfig = DefaultToThingDescriptionConfig.of(scopedConfig);
        tmBasedCreationConfig = DefaultTmBasedCreationConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultWotConfig of(final Config config) {
        return new DefaultWotConfig(DefaultScopedConfig.newInstance(config, CONFIG_PATH));
    }

    @Override
    public HttpProxyConfig getHttpProxyConfig() {
        return httpProxyConfig;
    }

    @Override
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    @Override
    public ToThingDescriptionConfig getToThingDescriptionConfig() {
        return toThingDescriptionConfig;
    }

    @Override
    public TmBasedCreationConfig getCreationConfig() {
        return tmBasedCreationConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultWotConfig that = (DefaultWotConfig) o;
        return Objects.equals(httpProxyConfig, that.httpProxyConfig) &&
                Objects.equals(cacheConfig, that.cacheConfig) &&
                Objects.equals(toThingDescriptionConfig, that.toThingDescriptionConfig) &&
                Objects.equals(tmBasedCreationConfig, that.tmBasedCreationConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpProxyConfig, cacheConfig, toThingDescriptionConfig, tmBasedCreationConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "httpProxyConfig=" + httpProxyConfig +
                ", cacheConfig=" + cacheConfig +
                ", toThingDescriptionConfig=" + toThingDescriptionConfig +
                ", tmBasedCreationConfig=" + tmBasedCreationConfig +
                "]";
    }

}
