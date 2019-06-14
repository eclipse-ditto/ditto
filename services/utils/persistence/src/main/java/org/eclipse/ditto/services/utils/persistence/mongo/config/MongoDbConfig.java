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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the service's MongoDB connection.
 */
@Immutable
public interface MongoDbConfig {

    /**
     * Returns the maximum query duration.
     *
     * @return the duration.
     */
    Duration getMaxQueryTime();

    /**
     * Retrieves the MongoDB URI from configured source URI and MongoDB settings.
     *
     * @return the URI adapted from source URI with parameters set according to MongoDB settings.
     */
    String getMongoDbUri();

    /**
     * Returns the configuration settings of the MongoDB options.
     *
     * @return the options config.
     */
    OptionsConfig getOptionsConfig();

    /**
     * Returns the configuration settings of the MongoDB connection pool.
     *
     * @return the connection pool config.
     */
    ConnectionPoolConfig getConnectionPoolConfig();

    /**
     * Returns the configuration settings of the MongoDB circuit breaker.
     *
     * @return the circuit breaker config.
     */
    CircuitBreakerConfig getCircuitBreakerConfig();

    /**
     * Returns the configuration settings of the MongoDB monitoring.
     *
     * @return the monitoring config.
     */
    MonitoringConfig getMonitoringConfig();

    /**
     * An enumeration of known value paths and associated default values of the MongoDbConfig.
     */
    enum MongoDbConfigValue implements KnownConfigValue {

        /**
         * The maximum query duration.
         */
        MAX_QUERY_TIME("maxQueryTime", Duration.ofMinutes(1L));

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
     * Provides configuration settings of the MongoDB options.
     */
    @Immutable
    interface OptionsConfig {

        /**
         * Indicates whether SSL should be enabled for the configured MongoDB source.
         *
         * @return {@code true} if SSL should be enabled, {@code false} else.
         */
        boolean isSslEnabled();

        /**
         * An enumeration of known value paths and associated default values of the OptionsConfig.
         */
        enum OptionsConfigValue implements KnownConfigValue {

            /**
             * Determines whether SSL should be enabled for the configured MongoDB source.
             */
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

    }

    /**
     * Provides configuration settings of the MongoDB connection pool.
     */
    @Immutable
    interface ConnectionPoolConfig {

        /**
         * Returns the maximum number of connections in the connection pool.
         *
         * @return the maximum number of connections.
         */
        int getMaxSize();

        /**
         * Returns the maximum number of threads waiting for a connection to become available.
         *
         * @return the maximum number of waiting threads.
         */
        int getMaxWaitQueueSize();

        /**
         * Returns the maximum time to wait for a connection to become available.
         *
         * @return the maximum wait time.
         */
        Duration getMaxWaitTime();

        /**
         * Indicates whether a JMX {@code ConnectionPoolListener} should be added.
         *
         * @return {@code true} if a JMX ConnectionPoolListener should be added, {@code false} else.
         */
        boolean isJmxListenerEnabled();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code ConnectionPoolConfig}.
         */
        enum ConnectionPoolConfigValue implements KnownConfigValue {

            /**
             * The maximum number of connections in the connection pool.
             */
            MAX_SIZE("maxSize", 100),

            /**
             * The maximum number of threads waiting for a connection to become available.
             */
            MAX_WAIT_QUEUE_SIZE("maxWaitQueueSize", 100),

            /**
             * The maximum time to wait for a connection to become available.
             */
            MAX_WAIT_TIME("maxWaitTime", Duration.ofSeconds(30L)),

            /**
             * Determines whether a JMX {@code ConnectionPoolListener} should be added.
             */
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

    }

    /**
     * Provides configuration settings of the MongoDB circuit breaker.
     */
    @Immutable
    interface CircuitBreakerConfig {

        /**
         * Returns the necessary amount of failures to be reached until the circuit breaker opens.
         *
         * @return the max failures amount.
         */
        int getMaxFailures();

        /**
         * Returns the configuration settings of the circuit breaker timeout.
         *
         * @return the circuit breaker timeout config.
         */
        TimeoutConfig getTimeoutConfig();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code CircuitBreakerConfig}.
         */
        enum CircuitBreakerConfigValue implements KnownConfigValue {

            /**
             * The necessary amount of failures to be reached until the circuit breaker opens.
             */
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

        /**
         * Provides configuration settings of the circuit breaker timeout.
         */
        @Immutable
        interface TimeoutConfig {

            /**
             * MongoDB timeouts cause the circuit breaker to open.
             * This method returns the amount of time to be reached until the circuit breaker opens.
             * If the duration is zero, timeouts won't open the circuit breaker.
             *
             * @return the duration to wait on MongoDB timeouts until the circuit breaker opens.
             */
            Duration getCall();

            /**
             * Returns the amount of time after which the circuit breaker is "half-opened" again.
             *
             * @return the duration after timeout until the circuit breaker becomes "half-opened".
             */
            Duration getReset();

            /**
             * An enumeration of the known config path expressions and their associated default values for
             * {@code TimeoutConfig}.
             */
            enum TimeoutConfigValue implements KnownConfigValue {

                /**
                 * The duration to wait on MongoDB timeouts until the circuit breaker opens.
                 */
                CALL("call", "5s"),

                /**
                 * The duration after timeout until the circuit breaker becomes "half-opened".
                 */
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

        }

    }

    /**
     * Provides the configuration settings for the MongoDB monitoring.
     */
    @Immutable
    interface MonitoringConfig {

        /**
         * Indicates whether all commands should be monitored and reported with Kamon.
         *
         * @return {@code true} if all commands should be monitored, {@code false} else.
         */
        boolean isCommandsEnabled();

        /**
         * Indicates whether connection pool statistics should be reported with Kamon.
         *
         * @return {@code true} if connection pool statistics should be reported, {@code false} else.
         */
        boolean isConnectionPoolEnabled();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code MonitoringConfig}.
         */
        enum MonitoringConfigValue implements KnownConfigValue {

            /**
             * Determines whether all commands should be monitored and reported with Kamon.
             */
            COMMANDS_ENABLED("commands", false),

            /**
             * Determines whether connection pool statistics should be reported with Kamon.
             */
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

    }

}
