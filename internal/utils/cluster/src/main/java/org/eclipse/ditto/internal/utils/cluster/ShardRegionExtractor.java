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
package org.eclipse.ditto.internal.utils.cluster;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.model.signals.ShardedMessageEnvelope;
import org.eclipse.ditto.json.JsonObject;

import akka.actor.ActorSystem;
import akka.cluster.sharding.ShardRegion;

/**
 * Implementation of {@link ShardRegion.MessageExtractor} which does a {@code hashCode} based sharding with the
 * configured amount of overall shards. This number has to be same on each cluster node.
 */
public final class ShardRegionExtractor implements ShardRegion.MessageExtractor {

    private final int numberOfShards;
    private final MappingStrategies mappingStrategies;
    private final ShardNumberCalculator shardNumberCalculator;

    private ShardRegionExtractor(final int numberOfShards, final MappingStrategies mappingStrategies) {
        this.numberOfShards = numberOfShards;
        this.mappingStrategies = mappingStrategies;
        shardNumberCalculator = ShardNumberCalculator.newInstance(numberOfShards);
    }

    /**
     * Returns a new {@code ShardRegionExtractor} by loading the {@link MappingStrategies} implementation to use
     * via the passed {@code ActorSystem}.
     *
     * @param numberOfShards the amount of shards to use.
     * @param actorSystem the ActorSystem to use for looking up the MappingStrategies.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @throws IllegalArgumentException if {@code numberOfShards} is less than one.
     */
    public static ShardRegionExtractor of(final int numberOfShards, final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var mappingStrategies = MappingStrategies.loadMappingStrategies(actorSystem);
        return new ShardRegionExtractor(numberOfShards, mappingStrategies);
    }

    /**
     * Returns a new {@code ShardRegionExtractor} with the given {@code numberOfShards} and a specific Map of
     * {@code mappingStrategies}.
     *
     * @param numberOfShards the amount of shards to use.
     * @param mappingStrategies the strategy for parsing incoming messages.
     * @throws NullPointerException if {@code mappingStrategies} is {@code null}.
     * @throws IllegalArgumentException if {@code numberOfShards} is less than one.
     */
    public static ShardRegionExtractor of(final int numberOfShards, final MappingStrategies mappingStrategies) {
        return new ShardRegionExtractor(numberOfShards, checkNotNull(mappingStrategies, "mappingStrategies"));
    }

    @Nullable
    @Override
    public String entityId(final Object message) {
        final String result;
        if (message instanceof WithEntityId withEntityId) {
            final var entityId = withEntityId.getEntityId();
            result = entityId.toString();
        } else if (message instanceof ShardRegion.StartEntity startEntity) {
            result = startEntity.entityId();
        } else {
            result = null;
        }
        return result;
    }

    @Nullable
    @Override
    public Object entityMessage(final Object message) {
        final Object entity;

        if (message instanceof JsonObject jsonObject) {
            // message was sent from another cluster node and therefore is serialized as json
            final ShardedMessageEnvelope shardedMessageEnvelope = ShardedMessageEnvelope.fromJson(jsonObject);
            entity = createJsonifiableFrom(shardedMessageEnvelope);
        } else if (message instanceof ShardedMessageEnvelope shardedMessageEnvelope) {
            // message was sent from the same cluster node
            entity = createJsonifiableFrom(shardedMessageEnvelope);
        } else {
            entity = message;
        }

        return entity;
    }

    @SuppressWarnings({"squid:S2676"})
    @Nullable
    @Override
    public String shardId(final Object message) {
        final String result;
        @Nullable final var entityId = entityId(message);
        if (null != entityId) {
            final var shardNumber = shardNumberCalculator.calculateShardNumber(entityId);
            result = String.valueOf(shardNumber);
        } else  {
            result = null;
        }
        return result;
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
        final JsonParsable<Jsonifiable<?>> mappingStrategy = mappingStrategies.getMappingStrategy(type)
                .orElseThrow(() -> {
                    final String pattern = "No strategy found to map type {0} to a Jsonifiable!";
                    throw new IllegalStateException(MessageFormat.format(pattern, type));
                });

        return mappingStrategy.parse(messageEnvelope.getMessage(), messageEnvelope.getDittoHeaders());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (ShardRegionExtractor) o;
        return numberOfShards == that.numberOfShards &&
                Objects.equals(mappingStrategies, that.mappingStrategies) &&
                Objects.equals(shardNumberCalculator, that.shardNumberCalculator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfShards, mappingStrategies, shardNumberCalculator);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "numberOfShards=" + numberOfShards + ", mappingStrategy="
                + mappingStrategies + "]";
    }

}
