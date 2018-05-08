/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;
import java.util.function.Function;

import org.eclipse.ditto.services.base.config.AbstractServiceConfigReader;

import com.typesafe.config.Config;

/**
 * Configuration reader for concierge service.
 */
public final class ConciergeConfigReader extends AbstractServiceConfigReader {

    private static final String PATH_CACHES = "caches";
    private static final String PATH_THINGS_AGGREGATOR_SINGLE_RETRIEVE_THING_TIMEOUT =
            "things-aggregator.single-retrieve-thing-timeout";

    private ConciergeConfigReader(final Config config, final String serviceName) {
        super(config, serviceName);
    }

    /**
     * Create configuration reader for concierge service.
     *
     * @param serviceName name of the concierge service.
     * @return function to create the configuration reader.
     */
    public static Function<Config, ConciergeConfigReader> from(final String serviceName) {
        return config -> new ConciergeConfigReader(config, serviceName);
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
     * Retrieve timeout how long the {@code ThingsAggregatorActor} should wait for a single retrieve thing.
     *
     * @return timeout how long to wait for a single retrieve thing.
     */
    public Duration thingsAggregatorSingleRetrieveThingTimeout() {
        return config.getDuration(PATH_THINGS_AGGREGATOR_SINGLE_RETRIEVE_THING_TIMEOUT);
    }

}
