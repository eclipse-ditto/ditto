/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.aggregation;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of things aggregation.
 */
@Immutable
public interface ThingsAggregatorConfig {

    /**
     * Returns the timeout how long the {@code ThingsAggregatorActor} should wait for a single retrieve thing.
     *
     * @return the timeout.
     */
    Duration getSingleRetrieveThingTimeout();

    /**
     * Returns the maximum parallelism, that is how many {@code RetrieveThing} commands can be "in flight" at the
     * same time towards the "things" service.
     *
     * @return the maximum parallelism.
     */
    int getMaxParallelism();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ThingsAggregatorConfig}.
     */
    enum ThingsAggregatorConfigValue implements KnownConfigValue {

        /**
         * The timeout how long the {@code ThingsAggregatorActor} should wait for a single retrieve thing.
         */
        SINGLE_RETRIEVE_THING_TIMEOUT("single-retrieve-thing-timeout", Duration.ofSeconds(30L)),

        /**
         * The maximum parallelism.
         */
        MAX_PARALLELISM("max-parallelism", 20);

        private final String path;
        private final Object defaultValue;

        ThingsAggregatorConfigValue(final String thePath, final Object theDefaultValue) {
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
