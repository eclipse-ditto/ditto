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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link MappingStrategies} for the Things service containing all {@link Jsonifiable} types known to Things.
 */
@Immutable
public final class ThingsMappingStrategies extends MappingStrategies {

    @Nullable private static ThingsMappingStrategies instance = null;

    private ThingsMappingStrategies(final Map<String, MappingStrategy> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Constructs a new ThingsMappingStrategies object.
     */
    public ThingsMappingStrategies() {
        this(getThingsMappingStrategies());
    }

    /**
     * Returns an instance of ThingsMappingStrategies.
     *
     * @return the instance.
     */
    public static ThingsMappingStrategies getInstance() {
        ThingsMappingStrategies result = instance;
        if (null == result) {
            result = new ThingsMappingStrategies(getThingsMappingStrategies());
            instance = result;
        }
        return result;
    }

    private static MappingStrategies getThingsMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .add(Thing.class, jsonObject -> ThingsModelFactory.newThing(jsonObject)) // do not replace with lambda!
                .add(ThingTag.class, jsonObject -> ThingTag.fromJson(jsonObject))  // do not replace with lambda!
                .add(BatchedEntityIdWithRevisions.typeOf(ThingTag.class),
                        BatchedEntityIdWithRevisions.deserializer(jsonObject -> ThingTag.fromJson(jsonObject)))
                .putAll(GlobalMappingStrategies.getInstance())
                .build();
    }

}
