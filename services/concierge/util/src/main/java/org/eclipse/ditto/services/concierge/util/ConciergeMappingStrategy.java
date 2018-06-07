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
package org.eclipse.ditto.services.concierge.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.concierge.batch.BatchMappingStrategy;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMappingStrategy;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategy;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategy;
import org.eclipse.ditto.services.models.thingsearch.ThingSearchMappingStrategy;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommandRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageErrorRegistry;

/**
 * {@link MappingStrategy} for the concierge service.
 */
public final class ConciergeMappingStrategy implements MappingStrategy {

    private final PoliciesMappingStrategy policiesMappingStrategy;
    private final ThingsMappingStrategy thingsMappingStrategy;
    private final ConnectivityMappingStrategy connectivityMappingStrategy;
    private final ThingSearchMappingStrategy thingSearchMappingStrategy;
    private final BatchMappingStrategy batchMappingStrategy;

    /**
     * Constructs a new Mapping Strategy.
     */
    public ConciergeMappingStrategy() {
        policiesMappingStrategy = new PoliciesMappingStrategy();
        thingsMappingStrategy = new ThingsMappingStrategy();
        connectivityMappingStrategy = new ConnectivityMappingStrategy(thingsMappingStrategy);
        thingSearchMappingStrategy = new ThingSearchMappingStrategy();
        batchMappingStrategy = new BatchMappingStrategy();
    }

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> combinedStrategy = new HashMap<>();
        combinedStrategy.putAll(policiesMappingStrategy.determineStrategy());
        combinedStrategy.putAll(thingSearchMappingStrategy.determineStrategy());
        combinedStrategy.putAll(connectivityMappingStrategy.determineStrategy());
        combinedStrategy.putAll(thingsMappingStrategy.determineStrategy());
        combinedStrategy.putAll(batchMappingStrategy.determineStrategy());

        final MappingStrategiesBuilder builder = MappingStrategiesBuilder.newInstance();

        addMessagesStrategies(builder);
        addDevOpsStrategies(builder);

        combinedStrategy.putAll(builder.build());
        return combinedStrategy;
    }

    private static void addMessagesStrategies(final MappingStrategiesBuilder builder) {
        builder.add(MessageCommandRegistry.newInstance());
        builder.add(MessageCommandResponseRegistry.newInstance());
        builder.add(MessageErrorRegistry.newInstance());
    }

    private static void addDevOpsStrategies(final MappingStrategiesBuilder builder) {
        builder.add(DevOpsCommandRegistry.newInstance());
        builder.add(DevOpsCommandResponseRegistry.newInstance());
    }
}
