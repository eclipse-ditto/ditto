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
package org.eclipse.ditto.gateway.service.util.config.health;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.health.config.BasicHealthCheckConfig;

/**
 * Provides configuration settings of the health checking of Gateway service.
 */
@Immutable
public interface HealthCheckConfig extends BasicHealthCheckConfig {

    /**
     * Returns the timeout used by the health check for determining the health of a single service.
     *
     * @return the timeout.
     */
    Duration getServiceTimeout();

    /**
     * Returns the configuration settings of the health checking regarding cluster roles.
     *
     * @return the config.
     */
    ClusterRolesConfig getClusterRolesConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code HealthCheckConfig}.
     */
    enum HealthCheckConfigValue implements KnownConfigValue {

        /**
         * The timeout used by the health check for determining the health of a single service.
         */
        SERVICE_TIMEOUT("service.timeout", Duration.ofSeconds(10));

        private final String path;
        private final Object defaultValue;

        HealthCheckConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

    /**
     * Provides configuration settings of the health checking regarding cluster roles.
     */
    @Immutable
    interface ClusterRolesConfig {

        /**
         * Indicates whether the health check for presence of all cluster roles should be enabled or not.
         *
         * @return {@code true} if the health check for presence of all cluster roles should be enabled, {@code false}
         * else.
         */
        boolean isEnabled();

        /**
         * Returns the roles the health check expected to be present in the running cluster.
         *
         * @return an unmodifiable unsorted Set containing the expected cluster roles.
         */
        Set<String> getExpectedClusterRoles();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code ClusterRolesConfig}.
         */
        enum ClusterRolesConfigValue implements KnownConfigValue {

            ENABLED("enabled", true),

            EXPECTED("expected",
                    Arrays.asList("policies", "things", "search", "gateway", "connectivity"));

            private final String path;
            private final Object defaultValue;

            ClusterRolesConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

        }

    }

}
