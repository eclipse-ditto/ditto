/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for namespace-scoped activity checks.
 * This allows different passivation intervals to be configured for entities in specific namespaces.
 *
 * @since 3.9.0
 */
@Immutable
public interface NamespaceActivityCheckConfig extends ActivityCheckConfig {

    /**
     * Returns the namespace pattern definition.
     * Supports SQL-LIKE wildcard patterns using '*' (matches any number of characters)
     * and '?' (matches any single character).
     *
     * @return the namespace pattern definition.
     */
    String getNamespacePattern();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code NamespaceActivityCheckConfig}.
     */
    enum NamespaceActivityCheckConfigValue implements KnownConfigValue {

        /**
         * The namespace pattern to apply the activity check configuration.
         */
        NAMESPACE_PATTERN("namespace-pattern", ""),

        /**
         * The interval of how long to keep an "inactive" entity in memory.
         */
        INACTIVE_INTERVAL("inactive-interval", Duration.ofHours(2L)),

        /**
         * The interval of how long to keep a deleted entity in memory.
         */
        DELETED_INTERVAL("deleted-interval", Duration.ofMinutes(5L));

        private final String configPath;
        private final Object defaultValue;

        NamespaceActivityCheckConfigValue(final String configPath, final Object defaultValue) {
            this.configPath = configPath;
            this.defaultValue = defaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return configPath;
        }
    }
}
