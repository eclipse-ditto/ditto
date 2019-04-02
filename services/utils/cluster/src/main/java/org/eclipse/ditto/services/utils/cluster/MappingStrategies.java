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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.json.Jsonifiable;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.reflect.ClassTag;
import scala.util.Try;

/**
 * Implementations define the mapping strategies for both persistence (JsonifiableSerializer) as well as Cluster
 * Sharding Mapping Strategies. As all {@code Command}s, {@code CommandResponse}s, {@code Event}s and {@code
 * DittoRuntimeException}s are {@link Jsonifiable} and transmitted in the cluster as JSON messages, this is needed in
 * each service which wants to participate in cluster communication.
 */
public interface MappingStrategies {

    String CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION = "ditto.mapping-strategy.implementation";

    Optional<MappingStrategy> getMappingStrategyFor(final String key);

    boolean containsMappingStrategyFor(final String key);

    Map<String, MappingStrategy> getStrategies();

    /**
     * Loads the {@link MappingStrategies} in the passed ActorSystem this is running in by looking up the config key
     * {@value CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION}.
     *
     * @param actorSystem the ActorSystem we are running in.
     * @return the resolved MappingStrategy.
     */
    static MappingStrategies loadMappingStrategy(final ActorSystem actorSystem) {
        // load via config the class implementing MappingStrategies:
        final String mappingStrategyClass =
                actorSystem.settings().config().getString(CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION);
        final ClassTag<MappingStrategies> tag = scala.reflect.ClassTag$.MODULE$.apply(MappingStrategies.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs = new ArrayList<>();
        final Try<MappingStrategies> mappingStrategy =
                ((ExtendedActorSystem) actorSystem).dynamicAccess().createInstanceFor(mappingStrategyClass,
                        JavaConversions.asScalaBuffer(constructorArgs).toList(), tag);
        return mappingStrategy.get();
    }

}
