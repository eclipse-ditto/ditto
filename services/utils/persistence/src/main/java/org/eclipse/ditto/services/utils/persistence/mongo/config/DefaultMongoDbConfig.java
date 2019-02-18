/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig}.
 */
@Immutable
public final class DefaultMongoDbConfig implements MongoDbConfig {

    /**
     * An enumeration of known value paths and associated default values of the MongoDbConfig.
     */
    enum MongoDbConfigValue implements KnownConfigValue {

        MAX_QUERY_TIME("maxQueryTime", "60s");

        private final String path;
        private final Object defaultValue;

        private MongoDbConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

    /**
     * The supposed path of the MongoDB config within the service Config object.
     */
    static final String CONFIG_PATH = "mongodb";

    private final Config config;
    private final String mongoDbUri;
    private final OptionsConfig optionsConfig;
    private final ConnectionPoolConfig connectionPoolConfig;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final MonitoringConfig monitoringConfig;

    private DefaultMongoDbConfig(final Config theConfig, final String theMongoDbUri) {
        config = theConfig;
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
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is {@code null} the value of
     * {@code config} at {@code configPath} is not of type {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static DefaultMongoDbConfig of(final Config config) {
        final Config configWithFallback = appendFallbackValues(config);

        return new DefaultMongoDbConfig(configWithFallback, determineMongoDbUri(configWithFallback));
    }

    private static Config appendFallbackValues(final Config config) {
        return ConfigWithFallback.newInstance(config, CONFIG_PATH, MongoDbConfigValue.values());
    }

    private static String determineMongoDbUri(final Config mongoDbConfig) {
        final MongoDbUriSupplier mongoDbUriSupplier = MongoDbUriSupplier.of(mongoDbConfig);
        return mongoDbUriSupplier.get();
    }

    @Override
    public Duration getMaxQueryTime() {
        return config.getDuration(MongoDbConfigValue.MAX_QUERY_TIME.getConfigPath());
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
        return config.equals(that.config) &&
                mongoDbUri.equals(that.mongoDbUri) &&
                optionsConfig.equals(that.optionsConfig) &&
                connectionPoolConfig.equals(that.connectionPoolConfig) &&
                circuitBreakerConfig.equals(that.circuitBreakerConfig) &&
                monitoringConfig.equals(that.monitoringConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, mongoDbUri, optionsConfig, connectionPoolConfig, circuitBreakerConfig,
                monitoringConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "config=" + config +
                ", mongoDbUri=" + mongoDbUri +
                ", optionsConfig=" + optionsConfig +
                ", connectionPoolConfig=" + connectionPoolConfig +
                ", circuitBreakerConfig=" + circuitBreakerConfig +
                ", monitoringConfig=" + monitoringConfig +
                "]";
    }

    /**
     * This class is the default implementation of {@link OptionsConfig}.
     */
    @Immutable
    public static final class DefaultOptionsConfig implements OptionsConfig {

        /**
         * An enumeration of known value paths and associated default values of the OptionsConfig.
         */
        enum OptionsConfigValue implements KnownConfigValue {

            SSL_ENABLED("ssl", false);

            private final String path;
            private final Object defaultValue;

            private OptionsConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

        /**
         * The supposed path of the OptionsConfig within the MongoDB config object.
         */
        static final String CONFIG_PATH = "options";

        private final Config config;

        private DefaultOptionsConfig(final Config theConfig) {
            config = theConfig;
        }

        /**
         * Returns an instance of {@code DefaultOptionsConfig} based on the settings of the specified Config.
         *
         * @param config is supposed to provide the settings of the options config at {@value #CONFIG_PATH}.
         * @return the instance.
         * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is {@code null} if the
         * value of {@code config} at {@code configPath} is not of type
         * {@link com.typesafe.config.ConfigValueType#OBJECT}.
         */
        public static DefaultOptionsConfig of(final Config config) {
            return new DefaultOptionsConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, OptionsConfigValue.values()));
        }

        @Override
        public boolean isSslEnabled() {
            return config.getBoolean(OptionsConfigValue.SSL_ENABLED.getConfigPath());
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DefaultOptionsConfig that = (DefaultOptionsConfig) o;
            return config.equals(that.config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "config=" + config +
                    "]";
        }

    }

    /**
     * This class is the default implementation of {@link ConnectionPoolConfig}.
     */
    @Immutable
    public static final class DefaultConnectionPoolConfig implements ConnectionPoolConfig {

        private enum ConnectionPoolConfigValue implements KnownConfigValue {

            MAX_SIZE("maxSize", 100),

            MAX_WAIT_QUEUE_SIZE("maxWaitQueueSize", 100),

            MAX_WAIT_TIME("maxWaitTime", "30s"),

            JMX_LISTENER_ENABLED("jmxListenerEnabled", false);

            private final String path;
            private final Object defaultValue;

            private ConnectionPoolConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

        private static final String CONFIG_PATH = "pool";

        private final Config config;

        private DefaultConnectionPoolConfig(final Config theConfig) {
            config = theConfig;
        }

        /**
         * Returns an instance of {@code DefaultConnectionPoolConfig} based on the settings of the specified Config.
         *
         * @param config is supposed to provide the settings of the connection pool config at {@value #CONFIG_PATH}.
         * @return the instance.
         * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is {@code null} if the
         * value of {@code config} at {@code configPath} is not of type
         * {@link com.typesafe.config.ConfigValueType#OBJECT}.
         */
        public static DefaultConnectionPoolConfig of(final Config config) {
            return new DefaultConnectionPoolConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, ConnectionPoolConfigValue.values()));
        }

        @Override
        public int getMaxSize() {
            return config.getInt(ConnectionPoolConfigValue.MAX_SIZE.getConfigPath());
        }

        @Override
        public int getMaxWaitQueueSize() {
            return config.getInt(ConnectionPoolConfigValue.MAX_WAIT_QUEUE_SIZE.getConfigPath());
        }

        @Override
        public Duration getMaxWaitTime() {
            return config.getDuration(ConnectionPoolConfigValue.MAX_WAIT_TIME.getConfigPath());
        }

        @Override
        public boolean isJmxListenerEnabled() {
            return config.getBoolean(ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getConfigPath());
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DefaultConnectionPoolConfig that = (DefaultConnectionPoolConfig) o;
            return config.equals(that.config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "config=" + config +
                    "]";
        }

    }

    /**
     * This class is the default implementation of {@link CircuitBreakerConfig}.
     */
    @Immutable
    public static final class DefaultCircuitBreakerConfig implements CircuitBreakerConfig {

        private enum CircuitBreakerConfigValue implements KnownConfigValue {

            MAX_FAILURES("maxFailures", 5);

            private final String path;
            private final Object defaultValue;

            private CircuitBreakerConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

        private static final String CONFIG_PATH = "breaker";

        private final Config config;
        private final TimeoutConfig timeoutConfig;

        private DefaultCircuitBreakerConfig(final Config theConfig) {
            config = theConfig;
            timeoutConfig = DefaultTimeoutConfig.of(config);
        }

        /**
         * Returns an instance of {@code DefaultCircuitBreakerConfig} based on the settings of the specified Config.
         *
         * @param config is supposed to provide the settings of the circuit breaker config at {@value #CONFIG_PATH}.
         * @return the instance.
         * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is {@code null} if the
         * value of {@code config} at {@code configPath} is not of type
         * {@link com.typesafe.config.ConfigValueType#OBJECT}.
         */
        public static DefaultCircuitBreakerConfig of(final Config config) {
            return new DefaultCircuitBreakerConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, CircuitBreakerConfigValue.values()));
        }

        @Override
        public int getMaxFailures() {
            return config.getInt(CircuitBreakerConfigValue.MAX_FAILURES.getConfigPath());
        }

        @Override
        public TimeoutConfig getTimeoutConfig() {
            return timeoutConfig;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DefaultCircuitBreakerConfig that = (DefaultCircuitBreakerConfig) o;
            return config.equals(that.config) && timeoutConfig.equals(that.timeoutConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config, timeoutConfig);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "config=" + config +
                    ", timeoutConfig=" + timeoutConfig +
                    "]";
        }

        /**
         * This class is the default implementation of {@link TimeoutConfig}.
         */
        @Immutable
        public static final class DefaultTimeoutConfig implements TimeoutConfig {

            private enum TimeoutConfigValue implements KnownConfigValue {

                CALL("call", "5s"),

                RESET("reset", "10s");

                private final String path;
                private final Object defaultValue;

                private TimeoutConfigValue(final String thePath, final Object theDefaultValue) {
                    path = thePath;
                    defaultValue = theDefaultValue;
                }

                @Override
                public String getConfigPath() {
                    return path;
                }

                @Override
                public Object getDefaultValue() {
                    return defaultValue;
                }

            }

            private static final String CONFIG_PATH = "timeout";

            private final Config config;

            private DefaultTimeoutConfig(final Config theConfig) {
                config = theConfig;
            }

            /**
             * Returns an instance of {@code DefaultTimeoutConfig} based on the settings of the specified Config.
             *
             * @param config is supposed to provide the settings of the timeout config at {@value #CONFIG_PATH}.
             * @return the instance.
             * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is {@code null} if the
             * value of {@code config} at {@code configPath} is not of type
             * {@link com.typesafe.config.ConfigValueType#OBJECT}.
             */
            public static DefaultTimeoutConfig of(final Config config) {
                return new DefaultTimeoutConfig(
                        ConfigWithFallback.newInstance(config, CONFIG_PATH, TimeoutConfigValue.values()));
            }

            @Override
            public Duration getCall() {
                return config.getDuration(TimeoutConfigValue.CALL.getConfigPath());
            }

            @Override
            public Duration getReset() {
                return config.getDuration(TimeoutConfigValue.RESET.getConfigPath());
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                final DefaultTimeoutConfig that = (DefaultTimeoutConfig) o;
                return config.equals(that.config);
            }

            @Override
            public int hashCode() {
                return Objects.hash(config);
            }

            @Override
            public String toString() {
                return getClass().getSimpleName() + " [" +
                        "config=" + config +
                        "]";
            }

        }

    }

    /**
     * This class is the default implementation of {@link MonitoringConfig}.
     */
    @Immutable
    public static final class DefaultMonitoringConfig implements MonitoringConfig {

        private enum MonitoringConfigValue implements KnownConfigValue {

            COMMANDS_ENABLED("commands", false),

            CONNECTION_POOL_ENABLED("connection-pool", false);

            private final String path;
            private final Object defaultValue;

            private MonitoringConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

        private static final String CONFIG_PATH = "monitoring";

        private final Config config;

        private DefaultMonitoringConfig(final Config theConfig) {
            config = theConfig;
        }

        /**
         * Returns an instance of {@code DefaultMonitoringConfig} based on the settings of the specified Config.
         *
         * @param config is supposed to provide the settings of the monitoring config at {@value #CONFIG_PATH}.
         * @return the instance.
         * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is {@code null} if the
         * value of {@code config} at {@code configPath} is not of type
         * {@link com.typesafe.config.ConfigValueType#OBJECT}.
         */
        public static DefaultMonitoringConfig of(final Config config) {
            return new DefaultMonitoringConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, MonitoringConfigValue.values()));
        }

        @Override
        public boolean isCommandsEnabled() {
            return config.getBoolean(MonitoringConfigValue.COMMANDS_ENABLED.getConfigPath());
        }

        @Override
        public boolean isConnectionPoolEnabled() {
            return config.getBoolean(MonitoringConfigValue.CONNECTION_POOL_ENABLED.getConfigPath());
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DefaultMonitoringConfig that = (DefaultMonitoringConfig) o;
            return config.equals(that.config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "config=" + config +
                    "]";
        }

    }

}
