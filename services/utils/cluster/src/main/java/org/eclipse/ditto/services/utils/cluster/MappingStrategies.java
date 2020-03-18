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
package org.eclipse.ditto.services.utils.cluster;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * An unmodifiable map of {@link MappingStrategy} elements associated with the name of the type the strategy yields.
 *
 * Implementations define the mapping strategies for both persistence (JsonifiableSerializer) as well as Cluster
 * Sharding Mapping Strategies.
 * As all {@code Command}s, {@code CommandResponse}s, {@code Event}s and {@code DittoRuntimeException}s are
 * {@code Jsonifiable} and transmitted in the cluster as JSON messages, this is needed in each service which wants to
 * participate in cluster communication.
 */
@Immutable
public abstract class MappingStrategies extends AbstractMap<String, MappingStrategy> {

    private static final String CONFIG_KEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION =
            "ditto.mapping-strategy.implementation";

    private final Map<String, MappingStrategy> strategies;

    /**
     * Constructs a new AbstractMappingStrategies object.
     *
     * @param strategies the key-value pairs of the returned mapping strategies.
     * @throws NullPointerException if {@code strategies} is {@code null}.
     */
    protected MappingStrategies(final Map<String, MappingStrategy> strategies) {
        this.strategies = Map.copyOf(checkNotNull(strategies, "strategies"));
    }

    /**
     * Loads the MappingStrategies in the passed ActorSystem this is running in by looking up the config key
     * {@value CONFIG_KEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION}.
     *
     * @param actorSystem the ActorSystem we are running in.
     * @return the resolved MappingStrategy.
     */
    public static MappingStrategies loadMappingStrategies(final ActorSystem actorSystem) {

        // Load via config the class implementing MappingStrategies:
        final ActorSystem.Settings settings = actorSystem.settings();
        final Config config = settings.config();
        final String mappingStrategyClass = config.getString(CONFIG_KEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION);

        return AkkaClassLoader.instantiate(actorSystem, MappingStrategies.class, mappingStrategyClass);
    }

    /**
     * Returns the associated mapping strategy for the given key.
     *
     * @param key the key to get the associated mapping strategy for.
     * @return an Optional containing the mapping strategy which is associated with the given {@code key} or an empty
     * Optional if the key is unknown.
     */
    public Optional<MappingStrategy> getMappingStrategy(@Nullable final String key) {
        if (null == key) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(key));
    }

    @Override
    public Set<Entry<String, MappingStrategy>> entrySet() {
        return strategies.entrySet();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final MappingStrategies that = (MappingStrategies) o;
        return strategies.equals(that.strategies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), strategies);
    }

}
