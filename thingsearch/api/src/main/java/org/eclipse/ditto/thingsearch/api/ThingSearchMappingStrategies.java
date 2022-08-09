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
package org.eclipse.ditto.thingsearch.api;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.policies.api.PoliciesMappingStrategies;
import org.eclipse.ditto.things.api.ThingsMappingStrategies;

/**
 * {@link MappingStrategies} for the Thing Search service containing all {@link Jsonifiable} types known to Things
 * Search.
 */
@Immutable
public final class ThingSearchMappingStrategies extends MappingStrategies {

    @Nullable private static ThingSearchMappingStrategies instance = null;

    private ThingSearchMappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Constructs a new ThingSearchMappingStrategies object.
     */
    @SuppressWarnings("unused") // used via reflection
    public ThingSearchMappingStrategies() {
        this(getThingSearchMappingStrategies());
    }

    /**
     * Returns an instance of ThingSearchMappingStrategies.
     *
     * @return the instance.
     */
    public static ThingSearchMappingStrategies getInstance() {
        ThingSearchMappingStrategies result = instance;
        if (null == result) {
            result = new ThingSearchMappingStrategies(getThingSearchMappingStrategies());
            instance = result;
        }
        return result;
    }

    private static MappingStrategies getThingSearchMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .putAll(PoliciesMappingStrategies.getInstance())
                .putAll(ThingsMappingStrategies.getInstance())
                .add(PolicyReferenceTag.class, PolicyReferenceTag::fromJson) // for inter-search instance communication
                .add(StreamedSnapshot.class.getSimpleName(),
                        (jsonObject, dittoHeaders) -> StreamedSnapshot.fromJson(jsonObject))
                .putAll(GlobalMappingStrategies.getInstance())
                .build();
    }

}
