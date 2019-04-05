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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandResponseRegistry;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;

/**
 * {@link org.eclipse.ditto.services.utils.cluster.MappingStrategies} for the Connectivity service containing all
 * {@link Jsonifiable} types known to this service.
 */
public final class ConnectivityMappingStrategies implements MappingStrategies {

    private final Map<String, MappingStrategy> strategies;

    /**
     * Constructs a new Mapping Strategy for Connectivity service.
     */
    public ConnectivityMappingStrategies() {
        this(new ThingsMappingStrategies());
    }

    /**
     * Constructs a new Mapping Strategy for Connectivity service.
     *
     * @param thingsMappingStrategy the existing ThingsMappingStrategy to use.
     */
    public ConnectivityMappingStrategies(final ThingsMappingStrategies thingsMappingStrategy) {
        strategies = Collections.unmodifiableMap(getConnectivityMappingStrategies(thingsMappingStrategy));
    }

    private Map<String, MappingStrategy> getConnectivityMappingStrategies(final MappingStrategies mappingStrategies) {
        final Map<String, MappingStrategy> combinedStrategies = new HashMap<>();

        final MappingStrategies strategies = MappingStrategiesBuilder.newInstance()
                .add(GlobalCommandRegistry.getInstance())
                .add(GlobalCommandResponseRegistry.getInstance())
                .add(GlobalEventRegistry.getInstance())
                .add(GlobalErrorRegistry.getInstance())
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

        combinedStrategies.putAll(strategies.getStrategies());
        combinedStrategies.putAll(mappingStrategies.getStrategies());

        return combinedStrategies;
    }

    @Override
    public Optional<MappingStrategy> getMappingStrategyFor(final String key) {
        return Optional.ofNullable(strategies.get(key));
    }

    @Override
    public boolean containsMappingStrategyFor(final String key) {
        return strategies.containsKey(key);
    }

    @Override
    public Map<String, MappingStrategy> getStrategies() {
        return strategies;
    }

}
