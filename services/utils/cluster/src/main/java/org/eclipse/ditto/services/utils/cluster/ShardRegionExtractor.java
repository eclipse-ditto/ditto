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

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.base.WithId;

import akka.actor.ActorSystem;
import akka.cluster.sharding.ShardRegion;


/**
 * Implementation of {@link ShardRegion.MessageExtractor} which does a {@code hashCode} based sharding with the with the
 * configured amount of overall shards. This number has to be same on each cluster node.
 */
public final class ShardRegionExtractor implements ShardRegion.MessageExtractor {

    private final int numberOfShards;
    private final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> mappingStrategies;

    private ShardRegionExtractor(final int numberOfShards,
            final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> mappingStrategies) {
        this.numberOfShards = numberOfShards;
        this.mappingStrategies = new HashMap<>();
        this.mappingStrategies.putAll(requireNonNull(mappingStrategies, "mapping strategies"));
    }

    /**
     * Returns a new {@code ShardRegionExtractor} by loading the {@link MappingStrategy} implementation to use via the
     * passed {@code ActorSystem}.
     *
     * @param numberOfShards the amount of shards to use.
     * @param actorSystem the ActorSystem to use for looking up the MappingStrategy.
     */
    public static ShardRegionExtractor of(final int numberOfShards, final ActorSystem actorSystem) {

        final MappingStrategy mappingStrategy = MappingStrategy.loadMappingStrategy(actorSystem);
        return new ShardRegionExtractor(numberOfShards, mappingStrategy.determineStrategy());
    }

    /**
     * Returns a new {@code ShardRegionExtractor} with the given {@code numberOfShards} and a specific Map of {@code
     * mappingStrategies}.
     *
     * @param numberOfShards the amount of shards to use.
     * @param mappingStrategies the strategies for parsing incoming messages.
     */
    public static ShardRegionExtractor of(final int numberOfShards,
            final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> mappingStrategies) {
        return new ShardRegionExtractor(numberOfShards, mappingStrategies);
    }

    @Override
    public String entityId(final Object message) {
        if (message instanceof ShardedMessageEnvelope) {
            return ((ShardedMessageEnvelope) message).getId();
        } else if (message instanceof WithId) {
            return ((WithId) message).getId();
        }
        return null;
    }

    @Override
    public Object entityMessage(final Object message) {
        final Object entity;

        if (message instanceof JsonObject) {
            // message was sent from another cluster node and therefor is serialized as json
            final ShardedMessageEnvelope shardedMessageEnvelope = ShardedMessageEnvelope.fromJson((JsonObject) message);
            entity = createJsonifiableFrom(shardedMessageEnvelope);
        } else if (message instanceof ShardedMessageEnvelope) {
            // message was sent from the same cluster node
            entity = createJsonifiableFrom((ShardedMessageEnvelope) message);
        } else {
            entity = message;
        }

        return entity;
    }

    @SuppressWarnings({"squid:S2676"})
    @Override
    public String shardId(final Object message) {
        final String entityId = entityId(message);
        if (entityId != null && entityId.hashCode() != Integer.MIN_VALUE) {
            return Integer.toString(Math.abs(entityId.hashCode()) % numberOfShards);
        }
        return null;
    }

    private Jsonifiable createJsonifiableFrom(final ShardedMessageEnvelope messageEnvelope) {
        final String type = messageEnvelope.getType();
        final BiFunction<JsonObject, DittoHeaders, Jsonifiable> mappingFunction = mappingStrategies.get(type);
        if (null == mappingFunction) {
            final String pattern = "No strategy found to map type {0} to a Jsonifiable!";
            throw new IllegalStateException(MessageFormat.format(pattern, type));
        }

        final JsonObject payload = messageEnvelope.getMessage();
        final DittoHeaders dittoHeaders = messageEnvelope.getDittoHeaders();

        return mappingStrategies.get(type).apply(payload, dittoHeaders);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        final ShardRegionExtractor that = (ShardRegionExtractor) o;
        return numberOfShards == that.numberOfShards && Objects.equals(mappingStrategies, that.mappingStrategies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfShards, mappingStrategies);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "numberOfShards=" + numberOfShards + ", mappingStrategies="
                + mappingStrategies + "]";
    }
}
