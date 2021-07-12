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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig}.
 */
@Immutable
public final class DefaultMongoDbConfig implements MongoDbConfig {

    /**
     * The supposed path of the MongoDB config within the service Config object.
     */
    static final String CONFIG_PATH = "mongodb";

    private final String mongoDbUri;
    private final Duration maxQueryTime;
    private final DefaultOptionsConfig optionsConfig;
    private final DefaultConnectionPoolConfig connectionPoolConfig;
    private final DefaultCircuitBreakerConfig circuitBreakerConfig;
    private final DefaultMonitoringConfig monitoringConfig;

    private DefaultMongoDbConfig(final ConfigWithFallback config) {
        maxQueryTime = config.getNonNegativeAndNonZeroDurationOrThrow(MongoDbConfigValue.MAX_QUERY_TIME);
        optionsConfig = DefaultOptionsConfig.of(config);
        final var configuredUri = config.getString(MongoDbConfigValue.URI.getConfigPath());
        final Map<String, Object> configuredExtraUriOptions = optionsConfig.extraUriOptions();

        final String sslKey = OptionsConfig.OptionsConfigValue.SSL_ENABLED.getConfigPath();
        final Map<String, Object> extraUriOptions;
        if (configuredExtraUriOptions.containsKey(sslKey)) {
            extraUriOptions = configuredExtraUriOptions;
        } else {
            extraUriOptions = new HashMap<>(configuredExtraUriOptions);
            extraUriOptions.put(sslKey, optionsConfig.isSslEnabled());
        }
        mongoDbUri = determineMongoDbUri(configuredUri, extraUriOptions);
        connectionPoolConfig = DefaultConnectionPoolConfig.of(config);
        circuitBreakerConfig = DefaultCircuitBreakerConfig.of(config);
        monitoringConfig = DefaultMonitoringConfig.of(config);
    }

    /**
     * Returns an instance of {@code MongoConfig} which tries to obtain its properties from the given Config.
     *
     * @param config the Config which contains nested MongoDB settings at path {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMongoDbConfig of(final Config config) {
        final var configWithFallback = appendFallbackValues(config);

        return new DefaultMongoDbConfig(configWithFallback);
    }

    private static ConfigWithFallback appendFallbackValues(final Config config) {
        return ConfigWithFallback.newInstance(config, CONFIG_PATH, MongoDbConfigValue.values());
    }

    private static String determineMongoDbUri(final String configuredMongoUri,
            final Map<String, Object> extraUriOptions) {
        final var mongoDbUriSupplier = MongoDbUriSupplier.of(configuredMongoUri, extraUriOptions);

        return mongoDbUriSupplier.get();
    }

    @Override
    public String getMongoDbUri() {
        return mongoDbUri;
    }

    @Override
    public Duration getMaxQueryTime() {
        return maxQueryTime;
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
        return Objects.equals(mongoDbUri, that.mongoDbUri) &&
                Objects.equals(maxQueryTime, that.maxQueryTime) &&
                Objects.equals(optionsConfig, that.optionsConfig) &&
                Objects.equals(connectionPoolConfig, that.connectionPoolConfig) &&
                Objects.equals(circuitBreakerConfig, that.circuitBreakerConfig) &&
                Objects.equals(monitoringConfig, that.monitoringConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mongoDbUri, maxQueryTime, optionsConfig, connectionPoolConfig, circuitBreakerConfig,
                monitoringConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mongoDbUri=" + mongoDbUri +
                ", maxQueryTime=" + maxQueryTime +
                ", optionsConfig=" + optionsConfig +
                ", connectionPoolConfig=" + connectionPoolConfig +
                ", circuitBreakerConfig=" + circuitBreakerConfig +
                ", monitoringConfig=" + monitoringConfig +
                "]";
    }

}
