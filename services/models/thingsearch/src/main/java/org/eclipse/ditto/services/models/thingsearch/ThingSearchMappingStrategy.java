/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.common.CommonCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandRegistry;
import org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommandRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommandResponseRegistry;

/**
 * {@link MappingStrategy} for the Thing Search service containing all {@link Jsonifiable} types known to Things Search.
 */
public final class ThingSearchMappingStrategy implements MappingStrategy {

    private final PoliciesMappingStrategy policiesMappingStrategy;
    private final ThingsMappingStrategy thingsMappingStrategy;

    /**
     * Constructs a new {@code ThingsMappingStrategy} object.
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

        builder.add(GlobalErrorRegistry.getInstance());

        addThingSearchStrategies(builder);
        addCommonStrategies(builder);
        addDevOpsStrategies(builder);
        addNamespacesStrategies(builder);

        combinedStrategy.putAll(builder.build());
        return combinedStrategy;
    }

    private static void addThingSearchStrategies(final MappingStrategiesBuilder builder) {
        builder.add(ThingSearchCommandRegistry.newInstance());
        builder.add(ThingSearchCommandResponseRegistry.newInstance());
        builder.add(ThingSearchSudoCommandRegistry.newInstance());
        builder.add(ThingSearchSudoCommandResponseRegistry.newInstance());
        builder.add(StreamingRegistry.newInstance());
    }

    private static void addCommonStrategies(final MappingStrategiesBuilder builder) {
        builder.add(CommonCommandRegistry.getInstance());
    }

    private static void addDevOpsStrategies(final MappingStrategiesBuilder builder) {
        builder.add(DevOpsCommandRegistry.newInstance());
        builder.add(DevOpsCommandResponseRegistry.newInstance());
    }

    private static void addNamespacesStrategies(final MappingStrategiesBuilder builder) {
        builder.add(NamespaceCommandRegistry.getInstance());
        builder.add(NamespaceCommandResponseRegistry.getInstance());
    }

}
