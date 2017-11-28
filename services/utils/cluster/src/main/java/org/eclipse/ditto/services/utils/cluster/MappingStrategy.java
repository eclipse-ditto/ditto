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
package org.eclipse.ditto.services.utils.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
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
public interface MappingStrategy {

    String CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION = "ditto.mapping-strategy.implementation";

    /**
     * Determines the mapping strategies from String {@code manifest} to a BiFunction from {@code (JsonObject,
     * DittoHeaders) -> (Jsonifiable)} with which to deserialize JsonObjects to Jsonifiables again.
     */
    Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy();

    /**
     * Loads the {@link MappingStrategy} in the passed ActorSystem this is running in by looking up the config key
     * {@value CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION}.
     *
     * @param actorSystem the ActorSystem we are running in.
     * @return the resolved MappingStrategy.
     */
    static MappingStrategy loadMappingStrategy(final ActorSystem actorSystem) {
        // load via config the class implementing MappingStrategy:
        final String mappingStrategyClass =
                actorSystem.settings().config().getString(CONFIGKEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION);
        final ClassTag<MappingStrategy> tag = scala.reflect.ClassTag$.MODULE$.apply(MappingStrategy.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs = new ArrayList<>();
        final Try<MappingStrategy> mappingStrategy =
                ((ExtendedActorSystem) actorSystem).dynamicAccess().createInstanceFor(mappingStrategyClass,
                        JavaConversions.asScalaBuffer(constructorArgs).toList(), tag);
        return mappingStrategy.get();
    }

}
