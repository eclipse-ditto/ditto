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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategy;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandRegistry;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommandRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponseRegistry;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEventRegistry;

/**
 * {@link MappingStrategy} for the Connectivity service containing all
 * {@link Jsonifiable} types known to this service.
 */
public final class ConnectivityMappingStrategy implements MappingStrategy {

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
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> combinedStrategy = new HashMap<>();

        final MappingStrategiesBuilder strategiesBuilder = MappingStrategiesBuilder.newInstance()
                .add(ConnectivityCommandRegistry.newInstance())
                .add(ConnectivityCommandResponseRegistry.newInstance())
                .add(ConnectivityEventRegistry.newInstance())
                .add(GlobalErrorRegistry.getInstance())
                .add(Connection.class, jsonObject ->
                        ConnectivityModelFactory.connectionFromJson(jsonObject)) // do not replace with lambda!
                .add("ImmutableConnection", jsonObject ->
                        ConnectivityModelFactory.connectionFromJson(jsonObject)) // do not replace with lambda!
                .add(OutboundSignal.class, jsonObject ->
                        OutboundSignalFactory.outboundSignalFromJson(jsonObject, this)) // do not replace with lambda!
                .add("UnmappedOutboundSignal", jsonObject ->
                        OutboundSignalFactory.outboundSignalFromJson(jsonObject, this)) // do not replace with lambda!
                .add(ResourceStatus.class, jsonObject ->
                        ConnectivityModelFactory.resourceStatusFromJson(jsonObject)) // do not replace with lambda!
                .add("ImmutableResourceStatus", jsonObject ->
                        ConnectivityModelFactory.resourceStatusFromJson(jsonObject)) // do not replace with lambda!
        ;

        addMessagesStrategies(strategiesBuilder);
        addDevOpsStrategies(strategiesBuilder);

        combinedStrategy.putAll(strategiesBuilder.build());
        combinedStrategy.putAll(thingsMappingStrategy.determineStrategy());

        return combinedStrategy;
    }

    private static void addMessagesStrategies(final MappingStrategiesBuilder builder) {
        builder.add(MessageCommandRegistry.newInstance());
        builder.add(MessageCommandResponseRegistry.newInstance());
    }

    private static void addDevOpsStrategies(final MappingStrategiesBuilder builder) {
        builder.add(DevOpsCommandRegistry.newInstance());
        builder.add(DevOpsCommandResponseRegistry.newInstance());
    }

}
