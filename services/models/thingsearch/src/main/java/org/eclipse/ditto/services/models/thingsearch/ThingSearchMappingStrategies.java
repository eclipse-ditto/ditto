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
package org.eclipse.ditto.services.models.thingsearch;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategies;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.AbstractGlobalMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link org.eclipse.ditto.services.utils.cluster.MappingStrategies} for the Thing Search service containing all {@link Jsonifiable} types known to Things Search.
 */
public final class ThingSearchMappingStrategies extends AbstractGlobalMappingStrategies {

    /**
     * Constructs a new {@code ThingsMappingStrategy} object.
     */
    public ThingSearchMappingStrategies() {
        super(getThingSearchMappingStrategies());
    }

    private static Map<String, MappingStrategy> getThingSearchMappingStrategies() {

        final Map<String, MappingStrategy> combinedStrategy = new HashMap<>();

        combinedStrategy.putAll(new PoliciesMappingStrategies().getStrategies());
        combinedStrategy.putAll(new ThingsMappingStrategies().getStrategies());

        return combinedStrategy;
    }
}
