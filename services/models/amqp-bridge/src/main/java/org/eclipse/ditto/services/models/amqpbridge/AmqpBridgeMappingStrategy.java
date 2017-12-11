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
package org.eclipse.ditto.services.models.amqpbridge;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategy;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommandRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageErrorRegistry;

import org.eclipse.ditto.signals.commands.amqpbridge.AmqpBridgeCommandRegistry;
import org.eclipse.ditto.signals.commands.amqpbridge.AmqpBridgeCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.amqpbridge.AmqpBridgeErrorRegistry;
import org.eclipse.ditto.signals.events.amqpbridge.AmqpBridgeEventRegistry;

/**
 * {@link org.eclipse.ditto.services.utils.cluster.MappingStrategy} for the Gateway service containing all {@link
 * org.eclipse.ditto.model.base.json.Jsonifiable} types known to this service.
 */
public final class AmqpBridgeMappingStrategy implements MappingStrategy {

    private final ThingsMappingStrategy thingsMappingStrategy;

    /**
     * Constructs a new Mapping Strategy for AMQP Bridge.
     */
    public AmqpBridgeMappingStrategy() {
        thingsMappingStrategy = new ThingsMappingStrategy();
    }

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> combinedStrategy = new HashMap<>();

        final MappingStrategiesBuilder strategiesBuilder = MappingStrategiesBuilder.newInstance()
                .add(AmqpBridgeCommandRegistry.newInstance())
                .add(AmqpBridgeCommandResponseRegistry.newInstance())
                .add(AmqpBridgeEventRegistry.newInstance())
                .add(AmqpBridgeErrorRegistry.newInstance());

        addMessagesStrategies(strategiesBuilder);
        addDevOpsStrategies(strategiesBuilder);

        combinedStrategy.putAll(strategiesBuilder.build());
        combinedStrategy.putAll(thingsMappingStrategy.determineStrategy());

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
