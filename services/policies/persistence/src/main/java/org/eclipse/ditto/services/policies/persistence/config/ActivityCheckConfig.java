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
package org.eclipse.ditto.services.policies.persistence.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for checking the activity of policy entities.
 * <p>
 * Java serialization is supported for {@code ActivityCheckConfig}.
 * </p>
 */
@Immutable
public interface ActivityCheckConfig {

    /**
     * Returns the interval of how long to keep an "inactive" Policy in memory.
     *
     * @return the interval.
     */
    Duration getInactiveInterval();

    /**
     * Returns the interval of how long to keep a deleted Policy in memory.
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
         * The interval of how long to keep an "inactive" Policy in memory.
         */
        INACTIVE_INTERVAL("inactive-interval", Duration.ofHours(2L)),

        /**
         * The interval of how long to keep a deleted Policy in memory.
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
