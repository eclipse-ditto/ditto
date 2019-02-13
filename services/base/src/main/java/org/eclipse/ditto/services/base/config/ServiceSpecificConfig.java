/*
 * Copyright (c) 2017-2019 Bosch Software Innovations GmbH.
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

/**
 * TODO Javadoc
 */
public interface ServiceSpecificConfig {

    ClusterConfig getClusterConfig();

    HealthCheckConfig getHealthCheckConfig();

    LimitsConfig getLimitsConfig();

    HttpConfig getHttpConfig();

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

    }

}
