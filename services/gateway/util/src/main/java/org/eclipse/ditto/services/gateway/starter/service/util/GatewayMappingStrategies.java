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
package org.eclipse.ditto.services.gateway.starter.service.util;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMappingStrategies;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategies;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategies;
import org.eclipse.ditto.services.models.thingsearch.ThingSearchMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.AbstractGlobalMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link org.eclipse.ditto.services.utils.cluster.MappingStrategies} for the Gateway service containing all {@link Jsonifiable} types known to Gateway.
 */
public final class GatewayMappingStrategies extends AbstractGlobalMappingStrategies {


    /**
     * Constructs a new {@code GatewayMappingStrategy} object.
     */
    public GatewayMappingStrategies() {
        super(getIndividualStrategies());
    }

    private static Map<String, MappingStrategy> getIndividualStrategies() {

        final Map<String, MappingStrategy> combinedStrategy = new HashMap<>();
        final ThingsMappingStrategies thingsMappingStrategy = new ThingsMappingStrategies();
        combinedStrategy.putAll(new PoliciesMappingStrategies().getStrategies());
        combinedStrategy.putAll(new ThingSearchMappingStrategies().getStrategies());
        combinedStrategy.putAll(new ConnectivityMappingStrategies(thingsMappingStrategy).getStrategies());
        combinedStrategy.putAll(thingsMappingStrategy.getStrategies());
        combinedStrategy.put(StreamingAck.class.getSimpleName(),
                (jsonObject, dittoHeaders) -> StreamingAck.fromJson(jsonObject));

        return combinedStrategy;
    }
}
