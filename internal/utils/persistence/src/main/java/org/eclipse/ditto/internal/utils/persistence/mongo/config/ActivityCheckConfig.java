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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for checking the activity of entities.
 */
@Immutable
public interface ActivityCheckConfig {

    /**
     * Returns the interval of how long to keep an "inactive" entity in memory.
     *
     * @return the interval.
     */
    Duration getInactiveInterval();

    /**
     * Returns the interval of how long to keep a deleted entity in memory.
     *
     * @return the interval.
     */
    Duration getDeletedInterval();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ActivityCheckConfig}.
     */
    enum ActivityCheckConfigValue implements KnownConfigValue {

        /**
         * The interval of how long to keep an "inactive" entity in memory.
         */
        INACTIVE_INTERVAL("inactive-interval", Duration.ofHours(2L)),

        /**
         * The interval of how long to keep a deleted entity in memory.
         */
        DELETED_INTERVAL("deleted-interval", Duration.ofMinutes(5L));

        private final String path;
        private final Object defaultValue;

        private ActivityCheckConfigValue(final String thePath, final Object theDefaultValue) {
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
