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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

@Immutable
public final class DefaultConnectionEnrichmentConfig implements ConnectionEnrichmentConfig {

    private static final String CONFIG_PATH = "connection-enrichment";
    private static final String FULL_CONFIG_PATH = "ditto.connectivity.connection-enrichment";

    private final int bufferSize;
    private final int parallelism;
    private final String provider;
    private final Config config;

    DefaultConnectionEnrichmentConfig(final ConfigWithFallback configWithFallback) {
        this.bufferSize = configWithFallback.getInt(ConnectionEnrichmentConfigValue.BUFFER_SIZE.getConfigPath());
        this.parallelism = configWithFallback.getInt(ConnectionEnrichmentConfigValue.PARALLELISM.getConfigPath());
        this.provider = configWithFallback.getString(ConnectionEnrichmentConfigValue.PROVIDER.getConfigPath());
        this.config = configWithFallback.getConfig(ConnectionEnrichmentConfigValue.CONFIG.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultConnectionEnrichmentConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultConnectionEnrichmentConfig of(final Config config) {
        return new DefaultConnectionEnrichmentConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                ConnectionEnrichmentConfigValue.values()));
    }

    static ConnectionEnrichmentConfig forActorSystem(final ActorSystem actorSystem) {
        return forActorSystemConfig(actorSystem.settings().config());
    }

    static ConnectionEnrichmentConfig forActorSystemConfig(final Config config) {
        return new DefaultConnectionEnrichmentConfig(
                ConfigWithFallback.newInstance(config, FULL_CONFIG_PATH, ConnectionEnrichmentConfigValue.values()));
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultConnectionEnrichmentConfig that = (DefaultConnectionEnrichmentConfig) o;
        return bufferSize == that.bufferSize &&
                parallelism == that.parallelism &&
                Objects.equals(provider, that.provider) &&
                Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bufferSize, parallelism, provider, config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "bufferSize=" + bufferSize +
                ", parallelism=" + parallelism +
                ", provider=" + provider +
                ", config=" + config +
                "]";
    }
}
