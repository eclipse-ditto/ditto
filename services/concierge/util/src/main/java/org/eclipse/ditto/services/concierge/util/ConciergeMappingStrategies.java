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
package org.eclipse.ditto.services.concierge.util;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.services.models.connectivity.ConnectivityMappingStrategies;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategies;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategies;
import org.eclipse.ditto.services.models.thingsearch.ThingSearchMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.AbstractGlobalMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link org.eclipse.ditto.services.utils.cluster.MappingStrategies} for the concierge service.
 */
public final class ConciergeMappingStrategies extends AbstractGlobalMappingStrategies {

    /**
     * Constructs a new {@code ConciergeMappingStrategy} object.
     */
    public ConciergeMappingStrategies() {
        super(getConciergeMappingStrategies());
    }

    private static Map<String, MappingStrategy> getConciergeMappingStrategies() {
        final ThingsMappingStrategies thingsMappingStrategy = new ThingsMappingStrategies();

        final Map<String, MappingStrategy> combinedStrategy = new HashMap<>();
        combinedStrategy.putAll(new PoliciesMappingStrategies().getStrategies());
        combinedStrategy.putAll(new ThingSearchMappingStrategies().getStrategies());
        combinedStrategy.putAll(new ConnectivityMappingStrategies(thingsMappingStrategy).getStrategies());
        combinedStrategy.putAll(thingsMappingStrategy.getStrategies());

        return combinedStrategy;
    }
}
