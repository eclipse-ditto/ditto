/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.base;

import javax.annotation.concurrent.Immutable;

/**
 * This marker interface provides enumerations of commonly known configuration keys. <em>This interface should not
 * be implemented by anyone.</em>
 */
@Immutable
public interface BaseConfigKey {

    /**
     * Retrieve the config suffix for this option.
     *
     * @return the config suffix
     */
    String getSuffix();

    /**
     * Return the config path for this option.
     *
     * @param servicePrefix prefix of the service, e. g., {@code ditto.things}
     * @return the config path
     */
    default String getConfigPath(final String servicePrefix) {
        return servicePrefix + getSuffix();
    }

    /**
     * Enumeration of keys for cluster configuration settings.
     */
    enum Cluster implements BaseConfigKey {

        /**
         * Key of the configuration setting which indicates whether the majority check is enabled.
         */
        MAJORITY_CHECK_ENABLED(".cluster.majority-check.enabled"),

        /**
         * Key of the majority check delay configuration setting.
         */
        MAJORITY_CHECK_DELAY(".cluster.majority-check.delay");

        private final String suffix;

        Cluster(final String suffix) {
            this.suffix = suffix;
        }

        @Override
        public String getSuffix() {
            return suffix;
        }
    }

    /**
     * Enumeration of keys for StatsD configuration settings.
     */
    enum StatsD implements BaseConfigKey {

        /**
         * Key of the StatsD hostname configuration setting.
         */
        HOSTNAME(".statsd.hostname"),

        /**
         * Key of the StatsD port configuration setting.
         */
        PORT(".statsd.port");

        private final String suffix;

        StatsD(final String suffix) {
            this.suffix = suffix;
        }

        @Override
        public String getSuffix() {
            return suffix;
        }
    }

}
