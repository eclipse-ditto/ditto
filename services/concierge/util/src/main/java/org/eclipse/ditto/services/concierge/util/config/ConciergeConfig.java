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
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;

/**
 * Provides the configuration settings of the Concierge service.
 */
@Immutable
public interface ConciergeConfig extends ServiceSpecificConfig, WithMongoDbConfig {

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
     */
    @Immutable
    interface EnforcementConfig {

        /**
         * Returns the ask timeout duration: the duration to wait for entity shard regions.
         *
         * @return the ask timeout duration.
         */
        Duration getAskTimeout();

    }

    /**
     * Provides configuration settings of the caches of Concierge.
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
         * Provides configuration settings of a particular cache of Concierge.
         */
        @Immutable
        interface CacheConfig {

            /**
             * Returns the maximum size of a cache.
             *
             * @return the maximum size.
             */
            long getMaximumSize();

            /**
             * Returns duration after which a cache entry expires.
             *
             * @return the duration between write and expiration.
             */
            Duration getExpireAfterWrite();

        }

    }

    /**
     * Provides the configuration settings of Concierge's things aggregation.
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

    }

}
