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
package org.eclipse.ditto.gateway.service.util;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.connectivity.api.ConnectivityMappingStrategies;
import org.eclipse.ditto.policies.api.PoliciesMappingStrategies;
import org.eclipse.ditto.things.api.ThingsMappingStrategies;
import org.eclipse.ditto.thingsearch.api.ThingSearchMappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.base.model.signals.JsonParsable;

/**
 * {@link MappingStrategies} for the Gateway service containing all {@link Jsonifiable} types known to Gateway.
 */
@Immutable
public final class GatewayMappingStrategies extends MappingStrategies {

    @Nullable private static GatewayMappingStrategies instance = null;

    private GatewayMappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Constructs a new GatewayMappingStrategies object.
     */
    @SuppressWarnings("unused") // used via reflection
    public GatewayMappingStrategies() {
        this(getGatewayMappingStrategies());
    }

    /**
     * Returns an instance of GatewayMappingStrategies.
     *
     * @return the instance.
     */
    public static GatewayMappingStrategies getInstance() {
        GatewayMappingStrategies result = instance;
        if (null == result) {
            result = new GatewayMappingStrategies(getGatewayMappingStrategies());
            instance = result;
        }
        return result;
    }

    private static MappingStrategies getGatewayMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .putAll(ThingsMappingStrategies.getInstance())
                .putAll(PoliciesMappingStrategies.getInstance())
                .putAll(ThingSearchMappingStrategies.getInstance())
                .putAll(ConnectivityMappingStrategies.getInstance())
                .putAll(GlobalMappingStrategies.getInstance())
                .build();
    }

}
