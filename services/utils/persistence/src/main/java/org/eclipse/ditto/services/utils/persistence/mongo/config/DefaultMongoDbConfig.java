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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig}.
 * <p>
 * Java serialization is supported for {@code DefaultMongoDbConfig}.
 * </p>
 */
@Immutable
public final class DefaultMongoDbConfig implements MongoDbConfig {

    /**
     * The supposed path of the MongoDB config within the service Config object.
     */
    static final String CONFIG_PATH = "mongodb";

    private final Duration maxQueryTime;
    private final String mongoDbUri;
    private final DefaultOptionsConfig optionsConfig;
    private final DefaultConnectionPoolConfig connectionPoolConfig;
    private final DefaultCircuitBreakerConfig circuitBreakerConfig;
    private final DefaultMonitoringConfig monitoringConfig;

    private DefaultMongoDbConfig(final ConfigWithFallback config, final String theMongoDbUri) {
        maxQueryTime = config.getDuration(MongoDbConfigValue.MAX_QUERY_TIME.getConfigPath());
        mongoDbUri = theMongoDbUri;
        optionsConfig = DefaultOptionsConfig.of(config);
        connectionPoolConfig = DefaultConnectionPoolConfig.of(config);
        circuitBreakerConfig = DefaultCircuitBreakerConfig.of(config);
        monitoringConfig = DefaultMonitoringConfig.of(config);
    }

    /**
     * Returns an instance of {@code MongoConfig} which tries to obtain its properties from the given Config.
     *
     * @param config the Config which contains nested MongoDB settings at path {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMongoDbConfig of(final Config config) {
        final ConfigWithFallback configWithFallback = appendFallbackValues(config);

        return new DefaultMongoDbConfig(configWithFallback, determineMongoDbUri(configWithFallback));
    }

    private static ConfigWithFallback appendFallbackValues(final Config config) {
        return ConfigWithFallback.newInstance(config, CONFIG_PATH, MongoDbConfigValue.values());
    }

    private static String determineMongoDbUri(final Config mongoDbConfig) {
        final MongoDbUriSupplier mongoDbUriSupplier = MongoDbUriSupplier.of(mongoDbConfig);
        return mongoDbUriSupplier.get();
    }

    @Override
    public Duration getMaxQueryTime() {
        return maxQueryTime;
    }

    @Override
    public String getMongoDbUri() {
        return mongoDbUri;
    }

    @Override
    public OptionsConfig getOptionsConfig() {
        return optionsConfig;
    }

    @Override
    public ConnectionPoolConfig getConnectionPoolConfig() {
        return connectionPoolConfig;
    }

    @Override
    public CircuitBreakerConfig getCircuitBreakerConfig() {
        return circuitBreakerConfig;
    }

    @Override
    public MonitoringConfig getMonitoringConfig() {
        return monitoringConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMongoDbConfig that = (DefaultMongoDbConfig) o;
        return Objects.equals(maxQueryTime, that.maxQueryTime) &&
                Objects.equals(mongoDbUri, that.mongoDbUri) &&
                Objects.equals(optionsConfig, that.optionsConfig) &&
                Objects.equals(connectionPoolConfig, that.connectionPoolConfig) &&
                Objects.equals(circuitBreakerConfig, that.circuitBreakerConfig) &&
                Objects.equals(monitoringConfig, that.monitoringConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxQueryTime, mongoDbUri, optionsConfig, connectionPoolConfig, circuitBreakerConfig,
                monitoringConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxQueryTime=" + maxQueryTime +
                ", mongoDbUri=" + mongoDbUri +
                ", optionsConfig=" + optionsConfig +
                ", connectionPoolConfig=" + connectionPoolConfig +
                ", circuitBreakerConfig=" + circuitBreakerConfig +
                ", monitoringConfig=" + monitoringConfig +
                "]";
    }

}
