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
package org.eclipse.ditto.services.thingsearch.common.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the persistence stream.
 */
@Immutable
public interface PersistenceStreamConfig extends StreamStageConfig {

    /**
     * Returns the amount of write operations to perform in one bulk.
     *
     * @return the max bulk size.
     */
    int getMaxBulkSize();

    /**
     * An enumeration of known config path expressions and their associated default values for
     * {@code PersistenceStreamConfig}.
     * This enumeration is a logical extension of {@link StreamStageConfigValue}.
     */
    enum PersistenceStreamConfigValue implements KnownConfigValue {

        /**
         * The amount of write operations to perform in one bulk.
         */
        MAX_BULK_SIZE("max-bulk-size", 250);

        private final String configPath;
        private final Object defaultValue;

        private PersistenceStreamConfigValue(final String configPath, final Object defaultValue) {
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
