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
package org.eclipse.ditto.services.models.things;

import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.streaming.StreamingRegistry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommandRegistry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommandResponseRegistry;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommandRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingErrorRegistry;
import org.eclipse.ditto.signals.events.things.ThingEventRegistry;

/**
 * {@link MappingStrategy} for the Things service containing all {@link Jsonifiable} types known to Things.
 */
public final class ThingsMappingStrategy implements MappingStrategy {

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final MappingStrategiesBuilder builder = MappingStrategiesBuilder.newInstance();

        addThingsStrategies(builder);
        addDevOpsStrategies(builder);

        return builder.build();
    }

    private static void addThingsStrategies(final MappingStrategiesBuilder builder) {
        builder
                .add(ThingErrorRegistry.newInstance())
                .add(ThingCommandRegistry.newInstance())
                .add(ThingCommandResponseRegistry.newInstance())
                .add(ThingEventRegistry.newInstance())
                .add(SudoCommandRegistry.newInstance())
                .add(SudoCommandResponseRegistry.newInstance())
                .add(StreamingRegistry.newInstance())
                .add(Thing.class,
                        (jsonObject) -> ThingsModelFactory.newThing(jsonObject)) // do not replace with lambda!
                .add(BaseCacheEntry.class,
                        jsonObject -> BaseCacheEntry.fromJson(jsonObject)) // do not replace with lambda!
                .add(ThingCacheEntry.class,
                        jsonObject -> ThingCacheEntry.fromJson(jsonObject)) // do not replace with lambda!
                .add(ThingTag.class, jsonObject -> ThingTag.fromJson(jsonObject))  // do not replace with lambda!
                .add(BatchedEntityIdWithRevisions.typeOf(ThingTag.class),
                        BatchedEntityIdWithRevisions.deserializer(jsonObject -> ThingTag.fromJson(jsonObject)))
                .build();
    }

    private static void addDevOpsStrategies(final MappingStrategiesBuilder builder) {
        builder.add(DevOpsCommandRegistry.newInstance());
        builder.add(DevOpsCommandResponseRegistry.newInstance());
    }
}
