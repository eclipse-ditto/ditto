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
package org.eclipse.ditto.things.api;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.internal.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * {@link MappingStrategies} for the Things service containing all {@link Jsonifiable} types known to Things.
 */
@Immutable
public final class ThingsMappingStrategies extends MappingStrategies {

    @Nullable private static ThingsMappingStrategies instance = null;

    private ThingsMappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Constructs a new ThingsMappingStrategies object.
     */
    @SuppressWarnings("unused") // used via reflection
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
                .add(Thing.class, jsonObject -> ThingsModelFactory.newThing(jsonObject)) // do not replace with lambda
                .add(PolicyTag.class, PolicyTag::fromJson)
                .putAll(GlobalMappingStrategies.getInstance())
                .build();
    }

}
