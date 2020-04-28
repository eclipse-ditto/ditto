/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.concierge;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.models.connectivity.ConnectivityMappingStrategies;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategies;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategies;
import org.eclipse.ditto.services.models.thingsearch.ThingSearchMappingStrategies;
import org.eclipse.ditto.services.utils.cache.InvalidateCacheEntry;
import org.eclipse.ditto.services.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link MappingStrategies} for the concierge service.
 */
@Immutable
public final class ConciergeMappingStrategies extends MappingStrategies {

    @Nullable private static ConciergeMappingStrategies instance = null;

    private ConciergeMappingStrategies(final Map<String, MappingStrategy> conciergeMappingStrategies) {
        super(conciergeMappingStrategies);
    }

    /**
     * Constructs a new ConciergeMappingStrategies object.
     */
    @SuppressWarnings("unused") // used via reflection
    public ConciergeMappingStrategies() {
        this(getConciergeMappingStrategies());
    }

    /**
     * Returns an instance of ConciergeMappingStrategies.
     *
     * @return the instance.
     */
    public static ConciergeMappingStrategies getInstance() {
        ConciergeMappingStrategies result = instance;
        if (null == result) {
            result = new ConciergeMappingStrategies(getConciergeMappingStrategies());
            instance = result;
        }
        return result;
    }

    private static Map<String, MappingStrategy> getConciergeMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .putAll(ThingsMappingStrategies.getInstance())
                .putAll(PoliciesMappingStrategies.getInstance())
                .putAll(ThingSearchMappingStrategies.getInstance())
                .putAll(ConnectivityMappingStrategies.getInstance())
                .add(InvalidateCacheEntry.class, jsonObject -> InvalidateCacheEntry.fromJson(jsonObject)) // do not replace with lambda!
                .putAll(GlobalMappingStrategies.getInstance())
                .build();
    }

}
