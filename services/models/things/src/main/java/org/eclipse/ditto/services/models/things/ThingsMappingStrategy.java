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
package org.eclipse.ditto.services.models.things;

import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandResponseRegistry;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;

/**
 * {@link MappingStrategy} for the Things service containing all {@link Jsonifiable} types known to Things.
 */
public final class ThingsMappingStrategy implements MappingStrategy {

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final MappingStrategiesBuilder builder = MappingStrategiesBuilder.newInstance();

        builder.add(GlobalErrorRegistry.getInstance())
                .add(GlobalCommandRegistry.getInstance())
                .add(GlobalCommandResponseRegistry.getInstance())
                .add(GlobalEventRegistry.getInstance());

        return addThingsStrategies(builder).build();
    }

    private static MappingStrategiesBuilder addThingsStrategies(final MappingStrategiesBuilder builder) {
        return builder.add(Thing.class,
                (jsonObject) -> ThingsModelFactory.newThing(jsonObject)) // do not replace with lambda!
                .add(ThingTag.class, jsonObject -> ThingTag.fromJson(jsonObject))  // do not replace with lambda!
                .add(BatchedEntityIdWithRevisions.typeOf(ThingTag.class),
                        BatchedEntityIdWithRevisions.deserializer(jsonObject -> ThingTag.fromJson(jsonObject)));
    }
}
