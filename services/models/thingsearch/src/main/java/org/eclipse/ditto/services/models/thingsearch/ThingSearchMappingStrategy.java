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
package org.eclipse.ditto.services.models.thingsearch;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategy;
import org.eclipse.ditto.services.models.streaming.StreamingRegistry;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategy;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommandRegistry;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommandResponseRegistry;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommandRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.ThingSearchErrorRegistry;

/**
 * {@link MappingStrategy} for the Thing Search service containing all {@link Jsonifiable} types known to Things Search.
 */
public final class ThingSearchMappingStrategy implements MappingStrategy {

    private final PoliciesMappingStrategy policiesMappingStrategy;
    private final ThingsMappingStrategy thingsMappingStrategy;

    /**
     * Constructs a new Mapping Strategy for Things Search.
     */
    public ThingSearchMappingStrategy() {
        policiesMappingStrategy = new PoliciesMappingStrategy();
        thingsMappingStrategy = new ThingsMappingStrategy();
    }

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> combinedStrategy = new HashMap<>();
        combinedStrategy.putAll(policiesMappingStrategy.determineStrategy());
        combinedStrategy.putAll(thingsMappingStrategy.determineStrategy());

        final MappingStrategiesBuilder builder = MappingStrategiesBuilder.newInstance();

        builder.add(BaseCacheEntry.class,
                jsonObject -> BaseCacheEntry.fromJson(jsonObject)); // do not replace with lambda!

        addThingSearchStrategies(builder);
        addDevOpsStrategies(builder);

        combinedStrategy.putAll(builder.build());
        return combinedStrategy;
    }

    private static void addThingSearchStrategies(final MappingStrategiesBuilder builder) {
        builder.add(ThingSearchCommandRegistry.newInstance());
        builder.add(ThingSearchCommandResponseRegistry.newInstance());
        builder.add(ThingSearchErrorRegistry.newInstance());
        builder.add(ThingSearchSudoCommandRegistry.newInstance());
        builder.add(ThingSearchSudoCommandResponseRegistry.newInstance());
        builder.add(StreamingRegistry.newInstance());
    }

    private static void addDevOpsStrategies(final MappingStrategiesBuilder builder) {
        builder.add(DevOpsCommandRegistry.newInstance());
        builder.add(DevOpsCommandResponseRegistry.newInstance());
    }

}
