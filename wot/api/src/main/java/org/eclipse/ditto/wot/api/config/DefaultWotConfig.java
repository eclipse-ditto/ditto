/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.api.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.http.DefaultHttpProxyBaseConfig;
import org.eclipse.ditto.internal.utils.config.http.HttpProxyBaseConfig;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the WoT (Web of Things) config.
 *
 * @since 2.4.0
 */
@Immutable
public final class DefaultWotConfig implements WotConfig {

    /**
     * The parent path of the "wot" config.
     */
    public static final String WOT_PARENT_CONFIG_PATH = "things";

    private static final String CONFIG_PATH = "wot";

    private final HttpProxyBaseConfig httpProxyConfig;
    private final CacheConfig cacheConfig;
    private final ToThingDescriptionConfig toThingDescriptionConfig;
    private final TmBasedCreationConfig tmBasedCreationConfig;
    private final TmValidationConfig tmValidationConfig;

    private DefaultWotConfig(final ScopedConfig scopedConfig) {
        httpProxyConfig = DefaultHttpProxyBaseConfig.ofHttpProxy(scopedConfig);
        cacheConfig = DefaultCacheConfig.of(scopedConfig, "cache");
        toThingDescriptionConfig = DefaultToThingDescriptionConfig.of(scopedConfig);
        tmBasedCreationConfig = DefaultTmBasedCreationConfig.of(scopedConfig);
        tmValidationConfig = DefaultTmValidationConfig.of(scopedConfig);
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
    public HttpProxyBaseConfig getHttpProxyConfig() {
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
    public TmValidationConfig getValidationConfig() {
        return tmValidationConfig;
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
                Objects.equals(tmBasedCreationConfig, that.tmBasedCreationConfig) &&
                Objects.equals(tmValidationConfig, that.tmValidationConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpProxyConfig, cacheConfig, toThingDescriptionConfig, tmBasedCreationConfig, tmValidationConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "httpProxyConfig=" + httpProxyConfig +
                ", cacheConfig=" + cacheConfig +
                ", toThingDescriptionConfig=" + toThingDescriptionConfig +
                ", tmBasedCreationConfig=" + tmBasedCreationConfig +
                ", tmValidationConfig=" + tmValidationConfig +
                "]";
    }

}
