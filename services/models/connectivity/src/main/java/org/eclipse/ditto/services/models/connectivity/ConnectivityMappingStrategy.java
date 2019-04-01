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
package org.eclipse.ditto.services.models.connectivity;

import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategy;
import org.eclipse.ditto.services.utils.cluster.AbstractMappingStrategy;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link MappingStrategy} for the Connectivity service containing all
 * {@link Jsonifiable} types known to this service.
 */
public final class ConnectivityMappingStrategy extends AbstractMappingStrategy {

    private final ThingsMappingStrategy thingsMappingStrategy;

    /**
     * Constructs a new Mapping Strategy for Connectivity service.
     */
    public ConnectivityMappingStrategy() {
        this(new ThingsMappingStrategy());
    }

    /**
     * Constructs a new Mapping Strategy for Connectivity service.
     *
     * @param thingsMappingStrategy the existing ThingsMappingStrategy to use.
     */
    public ConnectivityMappingStrategy(final ThingsMappingStrategy thingsMappingStrategy) {
        this.thingsMappingStrategy = thingsMappingStrategy;
    }

    @Override
    protected Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> getIndividualStrategies() {
        final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> combinedStrategy =
                MappingStrategiesBuilder.newInstance()
                        .add(Connection.class, jsonObject -> ConnectivityModelFactory.connectionFromJson(
                                jsonObject)) // do not replace with lambda!
                        .add("ImmutableConnection", jsonObject -> ConnectivityModelFactory.connectionFromJson(
                                jsonObject)) // do not replace with lambda!
                        .add(OutboundSignal.class,
                                jsonObject -> OutboundSignalFactory.outboundSignalFromJson(jsonObject,
                                        this)) // do not replace with lambda!
                        .add("UnmappedOutboundSignal",
                                jsonObject -> OutboundSignalFactory.outboundSignalFromJson(jsonObject,
                                        this)) // do not replace with lambda!
                        .add(ResourceStatus.class, jsonObject -> ConnectivityModelFactory.resourceStatusFromJson(
                                jsonObject)) // do not replace with lambda!
                        .add("ImmutableResourceStatus", jsonObject -> ConnectivityModelFactory.resourceStatusFromJson(
                                jsonObject)) // do not replace with lambda!
                        .build();
        combinedStrategy.putAll(thingsMappingStrategy.determineStrategy());

        return combinedStrategy;
    }

}
