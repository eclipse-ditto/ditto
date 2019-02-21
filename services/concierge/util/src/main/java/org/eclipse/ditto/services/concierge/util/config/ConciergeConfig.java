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
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.services.utils.config.WithConfigPath;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;

/**
 * Provides the configuration settings of the Concierge service.
 * <p>
 * Java serialization is supported for {@code ConciergeConfig}.
 * </p>
 */
@Immutable
public interface ConciergeConfig extends ServiceSpecificConfig, WithConfigPath, WithMongoDbConfig {

    /**
     * Returns the config of Concierge's enforcement behaviour.
     *
     * @return the config.
     */
    EnforcementConfig getEnforcementConfig();

    /**
     * Returns the config of Concierge's caches.
     *
     * @return the config.
     */
    CachesConfig getCachesConfig();

    /**
     * Returns the config of Concierge's things aggregation.
     *
     * @return the config.
     */
    ThingsAggregatorConfig getThingsAggregatorConfig();

    /**
     * Provides configuration settings for Concierge enforcement behaviour.
     * <p>
     * Java serialization is supported for {@code EnforcementConfig}.
     * </p>
     */
    @Immutable
    interface EnforcementConfig {

        /**
         * Returns the ask timeout duration: the duration to wait for entity shard regions.
         *
         * @return the ask timeout duration.
         */
        Duration getAskTimeout();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code EnforcementConfig}.
         */
        enum EnforcementConfigValue implements KnownConfigValue {

            /**
             * The ask timeout duration: the duration to wait for entity shard regions.
             */
            ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(10));

            private final String path;
            private final Object defaultValue;

            private EnforcementConfigValue(final String thePath, final Object theDefaultValue) {
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

    /**
     * Provides configuration settings of the caches of Concierge.
     * <p>
     * Java serialization is supported for {@code CachesConfig}.
     * </p>
     */
    @Immutable
    interface CachesConfig {

        /**
         * Returns the duration to wait for entity shard regions.
         *
         * @return the internal ask timeout duration.
         */
        Duration getAskTimeout();

        /**
         * Returns the config of the ID cache.
         *
         * @return the config.
         */
        CacheConfig getIdCacheConfig();

        /**
         * Returns the config of the enforcer cache.
         *
         * @return the config.
         */
        CacheConfig getEnforcerCacheConfig();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code CachesConfig}.
         */
        enum CachesConfigValue implements KnownConfigValue {

            /**
             * The duration to wait for entity shard regions.
             */
            ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(10L));

            private final String path;
            private final Object defaultValue;

            private CachesConfigValue(final String thePath, final Object theDefaultValue) {
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

    /**
     * Provides the configuration settings of Concierge's things aggregation.
     * <p>
     * Java serialization is supported for {@code ThingsAggregatorConfig}.
     * </p>
     */
    @Immutable
    interface ThingsAggregatorConfig {

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

            private ThingsAggregatorConfigValue(final String thePath, final Object theDefaultValue) {
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

}
