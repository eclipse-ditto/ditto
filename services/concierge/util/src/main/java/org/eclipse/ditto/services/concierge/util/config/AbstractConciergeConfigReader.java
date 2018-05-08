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

import org.eclipse.ditto.services.base.config.AbstractServiceConfigReader;

import com.typesafe.config.Config;

/**
 * Abstract base implementation Configuration reader for concierge service.
 */
public abstract class AbstractConciergeConfigReader extends AbstractServiceConfigReader {

    private static final String PATH_CACHES = "caches";
    private static final String PATH_ENFORCEMENT = "enforcement";

    private static final String PATH_THINGS_AGGREGATOR_SINGLE_RETRIEVE_THING_TIMEOUT =
            "things-aggregator.single-retrieve-thing-timeout";

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
}
