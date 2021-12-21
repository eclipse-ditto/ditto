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
package org.eclipse.ditto.internal.utils.cluster;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.base.model.signals.JsonParsable;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * An unmodifiable map of {@link JsonParsable} elements associated with the name of the type the strategy
 * yields.
 * <p>
 * Implementations define the mapping strategies for both persistence (JsonifiableSerializer) as well as Cluster
 * Sharding Mapping Strategies.
 * As all {@code Command}s, {@code CommandResponse}s, {@code Event}s and {@code DittoRuntimeException}s are
 * {@code Jsonifiable} and transmitted in the cluster as JSON messages, this is needed in each service which wants to
 * participate in cluster communication.
 */
@Immutable
public abstract class MappingStrategies implements Map<String, JsonParsable<Jsonifiable<?>>> {

    static final String CONFIG_KEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION =
            "ditto.mapping-strategy.implementation";

    private final Map<String, JsonParsable<Jsonifiable<?>>> strategies;

    /**
     * Constructs a new AbstractMappingStrategies object.
     *
     * @param strategies the key-value pairs of the returned mapping strategies.
     * @throws NullPointerException if {@code strategies} is {@code null}.
     */
    protected MappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> strategies) {
        this.strategies = Map.copyOf(checkNotNull(strategies, "strategies"));
    }

    /**
     * Loads the MappingStrategies in the passed ActorSystem this is running in by looking up the config key
     * {@value CONFIG_KEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION}.
     *
     * @param actorSystem the ActorSystem we are running in.
     * @return the resolved MappingStrategies.
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
    public Optional<JsonParsable<Jsonifiable<?>>> getMappingStrategy(@Nullable final String key) {
        if (null == key) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(key));
    }

    @Override
    public int size() {
        return strategies.size();
    }

    @Override
    public boolean isEmpty() {
        return strategies.isEmpty();
    }

    @Override
    public boolean containsKey(final Object o) {
        return strategies.containsKey(o);
    }

    @Override
    public boolean containsValue(final Object o) {
        return strategies.containsValue(o);
    }

    @Override
    public JsonParsable<Jsonifiable<?>> get(final Object o) {
        return strategies.get(o);
    }

    @Override
    public JsonParsable<Jsonifiable<?>> put(final String s,
            final JsonParsable<Jsonifiable<?>> jsonifiableJsonParsable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JsonParsable<Jsonifiable<?>> remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(final Map<? extends String, ? extends JsonParsable<Jsonifiable<?>>> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> keySet() {
        return strategies.keySet();
    }

    @Override
    public Collection<JsonParsable<Jsonifiable<?>>> values() {
        return strategies.values();
    }

    @Override
    public Set<Entry<String, JsonParsable<Jsonifiable<?>>>> entrySet() {
        return strategies.entrySet();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof Map) {
            return strategies.equals(o);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), strategies);
    }

}
