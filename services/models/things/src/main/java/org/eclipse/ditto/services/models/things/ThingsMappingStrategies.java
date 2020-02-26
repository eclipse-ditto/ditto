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

import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.utils.cluster.AbstractGlobalMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link org.eclipse.ditto.services.utils.cluster.MappingStrategies} for the Things service containing all {@link Jsonifiable} types known to Things.
 */
public final class ThingsMappingStrategies extends AbstractGlobalMappingStrategies {

    public ThingsMappingStrategies() {
        super(getThingsMappingStrategies());
    }

    private static Map<String, MappingStrategy> getThingsMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .add(Thing.class,
                        (jsonObject) -> ThingsModelFactory.newThing(jsonObject)) // do not replace with lambda!
                .add(ThingTag.class, jsonObject -> ThingTag.fromJson(jsonObject))  // do not replace with lambda!
                .add(BatchedEntityIdWithRevisions.typeOf(ThingTag.class),
                        BatchedEntityIdWithRevisions.deserializer(jsonObject -> ThingTag.fromJson(jsonObject)))
                .build().getStrategies();
    }

}
