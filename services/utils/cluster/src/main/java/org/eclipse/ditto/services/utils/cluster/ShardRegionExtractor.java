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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.base.WithId;

import akka.actor.ActorSystem;
import akka.cluster.sharding.ShardRegion;

/**
 * Implementation of {@link ShardRegion.MessageExtractor} which does a {@code hashCode} based sharding with the
 * configured amount of overall shards. This number has to be same on each cluster node.
 */
public final class ShardRegionExtractor implements ShardRegion.MessageExtractor {

    private final int numberOfShards;
    private final MappingStrategies mappingStrategies;

    private ShardRegionExtractor(final int numberOfShards, final MappingStrategies mappingStrategies) {
        this.numberOfShards = numberOfShards;
        this.mappingStrategies = checkNotNull(mappingStrategies, "mappingStrategies");
    }

    /**
     * Returns a new {@code ShardRegionExtractor} by loading the {@link MappingStrategies} implementation to use
     * via the passed {@code ActorSystem}.
     *
     * @param numberOfShards the amount of shards to use.
     * @param actorSystem the ActorSystem to use for looking up the MappingStrategy.
     */
    public static ShardRegionExtractor of(final int numberOfShards, final ActorSystem actorSystem) {
        final MappingStrategies mappingStrategies = MappingStrategies.loadMappingStrategies(actorSystem);
        return new ShardRegionExtractor(numberOfShards, mappingStrategies);
    }

    /**
     * Returns a new {@code ShardRegionExtractor} with the given {@code numberOfShards} and a specific Map of
     * {@code mappingStrategies}.
     *
     * @param numberOfShards the amount of shards to use.
     * @param mappingStrategy the strategy for parsing incoming messages.
     */
    public static ShardRegionExtractor of(final int numberOfShards, final MappingStrategies mappingStrategy) {
        return new ShardRegionExtractor(numberOfShards, mappingStrategy);
    }

    @Override
    public String entityId(final Object message) {
        if (message instanceof ShardedMessageEnvelope) {
            return ((ShardedMessageEnvelope) message).getEntityId().toString();
        } else if (message instanceof WithId) {
            return ((WithId) message).getEntityId().toString();
        } else if (message instanceof ShardRegion.StartEntity) {
            return ((ShardRegion.StartEntity) message).entityId();
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
        if (entityId != null) {
            final int hashcode = entityId.hashCode();
            // make sure not to negate Integer.MIN_VALUE because -Integer.MIN_VALUE == Integer.MIN_VALUE < 0.
            final int nonNegativeHashcode = hashcode == Integer.MIN_VALUE ? 0 : Math.abs(hashcode);
            return Integer.toString(nonNegativeHashcode % numberOfShards);
        }
        return null;
    }

    /**
     * Get shard IDs that are not active.
     *
     * @param activeShardIds what shard IDs are active.
     * @return the set of inactive shard IDs.
     */
    public Set<String> getInactiveShardIds(final Collection<String> activeShardIds) {
        final HashSet<String> remainingShardIds = new HashSet<>();
        IntStream.range(0, numberOfShards)
                .mapToObj(Integer::toString)
                .forEach(remainingShardIds::add);
        activeShardIds.forEach(remainingShardIds::remove);
        return remainingShardIds;
    }

    private Jsonifiable<?> createJsonifiableFrom(final ShardedMessageEnvelope messageEnvelope) {
        final String type = messageEnvelope.getType();
        final MappingStrategy mappingStrategy = mappingStrategies.getMappingStrategy(type)
                .orElseThrow(() -> {
                    final String pattern = "No strategy found to map type {0} to a Jsonifiable!";
                    throw new IllegalStateException(MessageFormat.format(pattern, type));
                });

        return mappingStrategy.map(messageEnvelope.getMessage(), messageEnvelope.getDittoHeaders());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ShardRegionExtractor that = (ShardRegionExtractor) o;
        return numberOfShards == that.numberOfShards && Objects.equals(mappingStrategies, that.mappingStrategies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfShards, mappingStrategies);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "numberOfShards=" + numberOfShards + ", mappingStrategy="
                + mappingStrategies + "]";
    }

}
