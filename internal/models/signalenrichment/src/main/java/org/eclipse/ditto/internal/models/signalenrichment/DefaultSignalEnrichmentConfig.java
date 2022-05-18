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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Default implementation of {@link SignalEnrichmentConfig}.
 */
@Immutable
public final class DefaultSignalEnrichmentConfig implements SignalEnrichmentConfig {

    private final String provider;
    private final Config config;

    private DefaultSignalEnrichmentConfig(final ConfigWithFallback configWithFallback) {
        provider = configWithFallback.getString(SignalEnrichmentConfigValue.PROVIDER.getConfigPath());
        config = configWithFallback.getConfig(SignalEnrichmentConfigValue.PROVIDER_CONFIG.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultConnectionEnrichmentConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSignalEnrichmentConfig of(final Config config) {
        return new DefaultSignalEnrichmentConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                SignalEnrichmentConfigValue.values()));
    }

    private static Class<?> getClassFromConfigString(final String configString) {
        try {
            return Class.forName(configString);
        } catch (final ClassNotFoundException e) {
            throw new DittoConfigError(e);
        }
    }

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public Config getProviderConfig() {
        return config;
    }

    @Override
    public Config render() {
        return ConfigFactory.empty()
                .withValue(SignalEnrichmentConfigValue.PROVIDER.getConfigPath(),
                        ConfigValueFactory.fromAnyRef(provider))
                .withValue(SignalEnrichmentConfigValue.PROVIDER_CONFIG.getConfigPath(), config.root())
                .atKey(CONFIG_PATH);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSignalEnrichmentConfig that = (DefaultSignalEnrichmentConfig) o;
        return Objects.equals(provider, that.provider)
                && Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "provider=" + provider +
                ", config=" + config +
                "]";
    }

}
