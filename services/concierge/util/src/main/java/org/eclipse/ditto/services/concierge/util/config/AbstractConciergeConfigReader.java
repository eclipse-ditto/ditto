/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;

import org.eclipse.ditto.services.base.config.AbstractServiceConfigReader;
import org.eclipse.ditto.services.utils.cache.config.CachesConfigReader;

import com.typesafe.config.Config;

/**
 * Abstract base implementation Configuration reader for concierge service.
 */
public abstract class AbstractConciergeConfigReader extends AbstractServiceConfigReader {

    private static final String PATH_CACHES = "caches";
    private static final String PATH_ENFORCEMENT = "enforcement";

    private static final String PATH_PREFIX_THINGS_AGGREGATOR = "things-aggregator.";

    private static final String PATH_THINGS_AGGREGATOR_SINGLE_RETRIEVE_THING_TIMEOUT =
            PATH_PREFIX_THINGS_AGGREGATOR + "single-retrieve-thing-timeout";

    private static final String PATH_THINGS_AGGREGATOR_MAX_PARALLELISM =
            PATH_PREFIX_THINGS_AGGREGATOR + "max-parallelism";


    protected AbstractConciergeConfigReader(final Config config, final String serviceName) {
        super(config, serviceName);
    }

    /**
     * Retrieve configuration reader of caches.
     *
     * @return the configuration reader.
     */
    public CachesConfigReader caches() {
        return new CachesConfigReader(getChild(PATH_CACHES));
    }

    /**
     * Retrieve a configuration reader for enforcement settings.
     *
     * @return the configuration reader.
     */
    public EnforcementConfigReader enforcement() {
        return new EnforcementConfigReader(getChild(PATH_ENFORCEMENT));
    }

    /**
     * Retrieve timeout how long the {@code ThingsAggregatorActor} should wait for a single retrieve thing.
     *
     * @return timeout how long to wait for a single retrieve thing.
     */
    public Duration thingsAggregatorSingleRetrieveThingTimeout() {
        return config.getDuration(PATH_THINGS_AGGREGATOR_SINGLE_RETRIEVE_THING_TIMEOUT);
    }

    /**
     * Retrieve the maximum parallelism, that is how many {@code RetrieveThing} commands can be "in flight" at the
     * same time towards the "things" service.
     *
     * @return the maximum parallelism.
     */
    public int thingsAggregatorMaxParallelism() {
        return config.getInt(PATH_THINGS_AGGREGATOR_MAX_PARALLELISM);
    }

}
