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
import java.util.Collections;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.mongodb.WriteConcern;

/**
 * Provides configuration settings for the service's MongoDB connection.
 */
@Immutable
public interface MongoDbConfig {

    /**
     * Retrieves the MongoDB URI from configured source URI and MongoDB settings.
     *
     * @return the URI adapted from source URI with parameters set according to MongoDB settings.
     */
    String getMongoDbUri();

    /**
     * Returns the maximum query duration.
     *
     * @return the duration.
     */
    Duration getMaxQueryTime();

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
         * The MongoDB URI - no default value in code provided.
         */
        URI("uri", null),

        /**
         * The maximum query duration.
         */
        MAX_QUERY_TIME("maxQueryTime", Duration.ofMinutes(1L));

        private final String path;
        private final Object defaultValue;

        MongoDbConfigValue(final String thePath, final Object theDefaultValue) {
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
         * Gets the desired read preference that should be used for accessing MongoDB.
         *
         * @return the desired read preference.
         */
        ReadPreference readPreference();

        /**
         * Gets the desired read concern that should be used for accessing MongoDB.
         *
         * @return the desired read concern.
         */
        ReadConcern readConcern();

        /**
         * Gets the desired write concern that should be used for writing to MongoDB.
         *
         * @return the desired write concern.
         */
        WriteConcern writeConcern();

        /**
         * Gets the desired "retryWrites" setting that should be used for writing to MongoDB.
         *
         * @return the desired "retryWrites".
         */
        boolean isRetryWrites();

        /**
         * Gets the extra options to add to the configured MongoDB {@code uri}.
         *
         * @return the extra options.
         */
        Map<String, Object> extraUriOptions();

        /**
         * An enumeration of known value paths and associated default values of the OptionsConfig.
         */
        enum OptionsConfigValue implements KnownConfigValue {

            /**
             * Determines whether SSL should be enabled for the configured MongoDB source.
             */
            SSL_ENABLED("ssl", false),

            /**
             * Determines the read preference used for MongoDB connections. See {@link ReadPreference} for available options.
             */
            READ_PREFERENCE("readPreference", "primaryPreferred"),

            /**
             * Determines the read concern used for MongoDB connections. See {@link ReadConcern} for available options.
             */
            READ_CONCERN("readConcern", "default"),

            /**
             * Determines the write concern used for MongoDB connections. See {@link com.mongodb.WriteConcern} for
             * available options.
             */
            WRITE_CONCERN("writeConcern", "acknowledged"),

            /**
             * Determines the "retryWrites" setting used for MongoDB connections.
             */
            RETRY_WRITES("retryWrites", true),

            /**
             * The extra options to add to the configured MongoDB {@code uri}.
             */
            EXTRA_URI_OPTIONS("extra-uri-options", Collections.<String, Object>emptyMap());

            private final String path;
            private final Object defaultValue;

            OptionsConfigValue(final String thePath, final Object theDefaultValue) {
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
         * Returns the minimum number of connections in the connection pool always to be kept alive.
         *
         * @return the minimum number of connections.
         */
        int getMinSize();

        /**
         * Returns the maximum number of connections in the connection pool.
         *
         * @return the maximum number of connections.
         */
        int getMaxSize();

        /**
         * Returns the maximum amount of time a pooled connection is allowed to idle before closing the connection.
         *
         * @return the maximum amount of time a pooled connection is allowed to idle.
         */
        Duration getMaxIdleTime();

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
             * The minimum number of connections in the connection pool.
             */
            MIN_SIZE("minSize", 0),

            /**
             * The maximum number of connections in the connection pool.
             */
            MAX_SIZE("maxSize", 100),

            /**
             * The maximum amount of time a pooled connection is allowed to idle before closing the connection.
             * Set to negative value to ignore and use Mongo Client default or value provided with URI.
             */
            MAX_IDLE_TIME("maxIdleTime", Duration.ofSeconds(-1)),

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

            ConnectionPoolConfigValue(final String thePath, final Object theDefaultValue) {
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

            CircuitBreakerConfigValue(final String thePath, final Object theDefaultValue) {
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

                TimeoutConfigValue(final String thePath, final Object theDefaultValue) {
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

            MonitoringConfigValue(final String thePath, final Object theDefaultValue) {
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
