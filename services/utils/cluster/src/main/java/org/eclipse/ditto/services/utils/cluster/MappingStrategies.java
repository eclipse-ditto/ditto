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
package org.eclipse.ditto.services.utils.cluster;

import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;

import akka.actor.ActorSystem;

/**
 * Implementations define the mapping strategies for both persistence (JsonifiableSerializer) as well as Cluster
 * Sharding Mapping Strategies.
 * As all {@code Command}s, {@code CommandResponse}s, {@code Event}s and {@code DittoRuntimeException}s are
 * {@link Jsonifiable} and transmitted in the cluster as JSON messages, this is needed in each service which wants to
 * participate in cluster communication.
 */
public interface MappingStrategies {

    String CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION = "ditto.mapping-strategy.implementation";

    Optional<MappingStrategy> getMappingStrategyFor(String key);

    boolean containsMappingStrategyFor(String key);

    Map<String, MappingStrategy> getStrategies();

    /**
     * Loads the MappingStrategies in the passed ActorSystem this is running in by looking up the config key
     * {@value CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION}.
     *
     * @param actorSystem the ActorSystem we are running in.
     * @return the resolved MappingStrategy.
     */
    static MappingStrategies loadMappingStrategies(final ActorSystem actorSystem) {
        // load via config the class implementing MappingStrategies:
        final String mappingStrategyClass =
                actorSystem.settings().config().getString(CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION);
        return AkkaClassLoader.instantiate(actorSystem, MappingStrategies.class, mappingStrategyClass);
    }

}
