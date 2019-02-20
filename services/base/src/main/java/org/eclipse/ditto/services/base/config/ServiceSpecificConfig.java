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
package org.eclipse.ditto.services.base.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the common configuration settings of each Ditto service.
 * This interface is the base of all service specific configuration settings.
 */
public interface ServiceSpecificConfig {

    /**
     * Returns the cluster config.
     *
     * @return the cluster config.
     */
    ClusterConfig getClusterConfig();

    /**
     * Returns the health check config.
     *
     * @return the health check config.
     */
    HealthCheckConfig getHealthCheckConfig();

    /**
     * Returns the limits config.
     *
     * @return the limits config.
     */
    LimitsConfig getLimitsConfig();

    /**
     * Returns the HTTP config.
     *
     * @return the HTTP config.
     */
    HttpConfig getHttpConfig();

    /**
     * Returns the metrics config.
     *
     * @return the metrics config.
     */
    MetricsConfig getMetricsConfig();

    /**
     * Provides configuration settings for the Ditto cluster.
     */
    @Immutable
    interface ClusterConfig {

        /**
         * Returns the number of shards in a cluster.
         *
         * @return the number of shards.
         */
        int getNumberOfShards();

        /**
         * TODO
         */
        enum ClusterConfigValue implements KnownConfigValue {

            NUMBER_OF_SHARDS("number-of-shards", 30);

            private final String path;
            private final Object defaultValue;

            private ClusterConfigValue(final String thePath, final Object theDefaultValue) {
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
     * Provides configuration settings regarded to Ditto's runtime health.
     */
    @Immutable
    interface HealthCheckConfig {

        /**
         * Indicates whether global health checking should be enabled.
         *
         * @return {@code true} if health checking should be enabled, {@code false} else.
         */
        boolean isEnabled();

        /**
         * Returns the interval of health check.
         *
         * @return the interval.
         * @see #isEnabled()
         */
        Duration getInterval();

        /**
         * Indicates whether the persistence health check should be enabled.
         *
         * @return {@code true} if the persistence health check should be enabled, {@code false} else.
         */
        boolean isPersistenceEnabled();

        /**
         * Returns the timeout of the health check for persistence.
         * If the persistence takes longer than that to respond, it is considered "DOWN".
         *
         * @return the timeout of the health check for persistence.
         * @see #isPersistenceEnabled()
         */
        Duration getPersistenceTimeout();

        /**
         * TODO
         */
        enum HealthCheckConfigValue implements KnownConfigValue {

            ENABLED("enabled", true),

            INTERVAL("interval", Duration.ofMinutes(1L)),

            PERSISTENCE_ENABLED("persistence.enabled", false),

            PERSISTENCE_TIMEOUT("persistence.timeout", Duration.ofMinutes(1));

            private final String path;
            private final Object defaultValue;

            private HealthCheckConfigValue(final String thePath, final Object theDefaultValue) {
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
     * Provides configuration settings for the limits of Ditto services.
     */
    @Immutable
    interface LimitsConfig {

        /**
         * Returns the maximum possible size of "Thing" entities in bytes.
         *
         * @return max size in bytes.
         */
        long getThingsMaxSize();

        /**
         * Returns the maximum possible size of "Policies" entities in bytes.
         *
         * @return max size in bytes.
         */
        long getPoliciesMaxSize();

        /**
         * Returns the maximum possible size of "Policies" entities in bytes.
         *
         * @return max size in bytes.
         */
        long getMessagesMaxSize();

        /**
         * Returns the default pagination size to apply when searching for "Things" via "things-search".
         *
         * @return default pagination size.
         */
        int getThingsSearchDefaultPageSize();

        /**
         * Retrieve the maximum pagination size to apply when searching for "Things" via "things-search".
         *
         * @return max pagination size.
         */
        int thingsSearchMaxPageSize();

        /**
         * TODO
         */
        enum LimitsConfigValue implements KnownConfigValue {

            THINGS_MAX_SIZE("things.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

            POLICIES_MAX_SIZE("policies.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

            MESSAGES_MAX_SIZE("messages.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

            THINGS_SEARCH_DEFAULT_PAGE_SIZE(Constants.THINGS_SEARCH_KEY + "." + "default-page-size", 25),

            THINGS_SEARCH_MAX_PAGE_SIZE(Constants.THINGS_SEARCH_KEY + "." + "max-page-size", 200);

            private final String path;
            private final Object defaultValue;

            private LimitsConfigValue(final String thePath, final Object theDefaultValue) {
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

            /**
             * TODO
             */
            public static final class Constants {

                /**
                 * TODO
                 */
                public static final long DEFAULT_ENTITY_MAX_SIZE = 100 * 1024L;

                /**
                 * TODO
                 */
                public static final String THINGS_SEARCH_KEY = "things-search";

                private Constants() {
                    throw new AssertionError();
                }

            }

        }

    }

    /**
     * Provides the configuration settings of the Ditto HTTP endpoint.
     */
    @Immutable
    interface HttpConfig {

        /**
         * Returns the hostname value of the HTTP endpoint.
         *
         * @return an Optional containing the hostname or an empty Optional if no host name was configured.
         */
        String getHostname();

        /**
         * Returns the port number of the HTTP endpoint.
         *
         * @return an Optional containing the port number or an empty Optional if no port number was configured.
         */
        int getPort();

        /**
         * TODO
         */
        enum HttpConfigValue implements KnownConfigValue {

            HOSTNAME("hostname", ""),

            PORT("port", 8080);

            private final String path;
            private final Object defaultValue;

            private HttpConfigValue(final String thePath, final Object theDefaultValue) {
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
     * Provides the configuration settings of metrics.
     */
    @Immutable
    interface MetricsConfig {

        /**
         * Indicates whether system metrics are enabled.
         *
         * @return {@code true} if system metrics are enabled, {@code false} if not.
         */
        boolean isSystemMetricsEnabled();

        /**
         * Indicates whether Prometheus is enabled.
         *
         * @return {@code true} if Prometheus is enabled, {@code false} if not.
         */
        boolean isPrometheusEnabled();

        /**
         * Returns the hostname to bind the Prometheus HTTP server to.
         *
         * @return the hostname.
         */
        String getPrometheusHostname();

        /**
         * Returns the port to bind the Prometheus HTTP server to.
         *
         * @return the port.
         */
        int getPrometheusPort();

        /**
         * TODO
         */
        enum MetricsConfigValue implements KnownConfigValue {

            SYSTEM_METRICS_ENABLED("systemMetrics.enabled", false),

            PROMETHEUS_ENABLED("prometheus.enabled", false),

            PROMETHEUS_HOSTNAME("prometheus.hostname", "0.0.0.0"),

            PROMETHEUS_PORT("prometheus.port", 9095);

            private final String path;
            private final Object defaultValue;

            private MetricsConfigValue(final String thePath, final Object theDefaultValue) {
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
