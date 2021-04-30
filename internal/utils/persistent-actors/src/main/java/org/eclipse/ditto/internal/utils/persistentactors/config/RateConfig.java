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
package org.eclipse.ditto.internal.utils.persistentactors.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for throttling the pinging of persistence actors.
 * The goal of this throttling is to achieve that not all persistence actors are recovered at the same time.
 */
@Immutable
public interface RateConfig {

    /**
     * Returns the duration (frequency) of recovery.
     * This value is used to limit the recovery rate.
     *
     * @return the frequency.
     */
    Duration getFrequency();

    /**
     * Returns the number of entities to be recovered per batch.
     * This value is used to limit the recovery rate.
     *
     * @return the number of entities.
     */
    int getEntityAmount();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code RateConfig}.
     */
    enum RateConfigValue implements KnownConfigValue {

        /**
         * Returns the duration (frequency) of recovery.
         */
        FREQUENCY("frequency", Duration.ofSeconds(1L)),

        /**
         * Returns the number of entities to be recovered per batch.
         */
        ENTITIES("entities", 1);

        private final String path;
        private final Object defaultValue;

        RateConfigValue(final String thePath, final Object theDefaultValue) {
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
