/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.common.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadPreference;

/**
 * Provides configuration settings of the Search query and updater persistence.
 */
@Immutable
public interface SearchPersistenceConfig {

    /**
     * Gets the desired read preference that should be used for queries done in the persistence.
     *
     * @return the desired read preference.
     */
    ReadPreference readPreference();

    /**
     * Gets the desired read concern that should be used for queries done in the persistence.
     *
     * @return the desired read concern.
     */
    ReadConcern readConcern();

    /**
     * An enumeration of known config path expressions and their associated default values for {@code SearchPersistenceConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Determines the read preference used for MongoDB connections. See {@link ReadPreference} for available options.
         */
        READ_PREFERENCE("readPreference", "primaryPreferred"),

        /**
         * Determines the read concern used for MongoDB connections. See {@link ReadConcern} for available options.
         */
        READ_CONCERN("readConcern", "default");

        private final String configPath;
        private final Object defaultValue;

        ConfigValue(final String configPath, final Object defaultValue) {
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
