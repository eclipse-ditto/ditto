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
package org.eclipse.ditto.services.concierge.common;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for persistence cleanup actions.
 */
public interface PersistenceCleanupConfig {

    /**
     * Returns how long to wait before scheduling persistence cleanup actions.
     *
     * @return duration of the quiet period.
     */
    Duration getQuietPeriod();

    /**
     * Returns configuration settings for credit decision.
     *
     * @return the config.
     */
    CreditDecisionConfig getCreditDecisionConfig();

    /**
     * Returns configuration settings for the persistence ID stream.
     *
     * @return the config.
     */
    PersistenceIdsConfig getPersistenceIdsConfig();

    /**
     * Enumeration of known config keys and default values for {@code PersistenceCleanupConfig}
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Duration between service start-up and the beginning of cleanup actions.
         */
        QUIET_PERIOD("quiet-period", Duration.ofMinutes(5L));

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
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
