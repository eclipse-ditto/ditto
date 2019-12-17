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
final class DefaultConnectionEnrichmentConfig implements ConnectionEnrichmentConfig {

    private static final String CONFIG_PATH = "ditto.connectivity.connection-enrichment";

    private final String provider;
    private final Config config;

    DefaultConnectionEnrichmentConfig(final ConfigWithFallback configWithFallback) {
        this.provider = configWithFallback.getString(ConfigValue.PROVIDER.getConfigPath());
        this.config = configWithFallback.getConfig(ConfigValue.CONFIG.getConfigPath());
    }

    static ConnectionEnrichmentConfig forActorSystem(final ActorSystem actorSystem) {
        return forActorSystemConfig(actorSystem.settings().config());
    }

    static ConnectionEnrichmentConfig forActorSystemConfig(final Config config) {
        return new DefaultConnectionEnrichmentConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
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
        if (o instanceof DefaultConnectionEnrichmentConfig) {
            final DefaultConnectionEnrichmentConfig that = (DefaultConnectionEnrichmentConfig) o;
            return Objects.equals(provider, that.provider) && Objects.equals(config, that.config);
        } else {
            return false;
        }
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
