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
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMappingStrategy;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategy;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategy;
import org.eclipse.ditto.services.models.thingsearch.ThingSearchMappingStrategy;
import org.eclipse.ditto.services.utils.cluster.AbstractMappingStrategy;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link MappingStrategy} for the Gateway service containing all {@link Jsonifiable} types known to Gateway.
 */
public final class GatewayMappingStrategy extends AbstractMappingStrategy {

    private final PoliciesMappingStrategy policiesMappingStrategy;
    private final ThingsMappingStrategy thingsMappingStrategy;
    private final ConnectivityMappingStrategy connectivityMappingStrategy;
    private final ThingSearchMappingStrategy thingSearchMappingStrategy;

    /**
     * Constructs a new {@code GatewayMappingStrategy} object.
     */
    public GatewayMappingStrategy() {
        policiesMappingStrategy = new PoliciesMappingStrategy();
        thingsMappingStrategy = new ThingsMappingStrategy();
        connectivityMappingStrategy = new ConnectivityMappingStrategy(thingsMappingStrategy);
        thingSearchMappingStrategy = new ThingSearchMappingStrategy();
    }

    @Override
    protected Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> getIndividualStrategies() {

        final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> combinedStrategy = new HashMap<>();

        combinedStrategy.putAll(policiesMappingStrategy.determineStrategy());
        combinedStrategy.putAll(thingSearchMappingStrategy.determineStrategy());
        combinedStrategy.putAll(connectivityMappingStrategy.determineStrategy());
        combinedStrategy.putAll(thingsMappingStrategy.determineStrategy());
        combinedStrategy.put(StreamingAck.class.getSimpleName(),
                (jsonObject, dittoHeaders) -> StreamingAck.fromJson(jsonObject));

        return combinedStrategy;
    }
}
