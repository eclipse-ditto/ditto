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
package org.eclipse.ditto.internal.utils.pubsub.ddata.compressed;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralUpdate;
import org.eclipse.ditto.internal.utils.pubsub.ddata.AbstractSubscriptions;
import org.eclipse.ditto.internal.utils.pubsub.ddata.Hashes;
import org.eclipse.ditto.internal.utils.pubsub.ddata.SubscriberData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.TopicData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.Grouped;

import akka.actor.ActorRef;

/**
 * Local subscriptions for distribution of subscribed topics as hash code sequences.
 */
@NotThreadSafe
public final class CompressedSubscriptions extends AbstractSubscriptions<String, LiteralUpdate>
        implements Hashes {

    /**
     * Seeds of hash functions. They should be identical cluster-wide.
     */
    private final Collection<Integer> seeds;

    private CompressedSubscriptions(
            final Collection<Integer> seeds,
            final Map<ActorRef, SubscriberData> subscriberDataMap,
            final Map<String, TopicData> topicToData) {
        super(subscriberDataMap, topicToData);
        this.seeds = seeds;
    }

    /**
     * Create a new compressed subscriptions object.
     *
     * @param seeds seeds of the family of hash functions..
     * @return the compressed subscriptions object.
     */
    public static CompressedSubscriptions of(final Collection<Integer> seeds) {
        return new CompressedSubscriptions(seeds, new HashMap<>(), new HashMap<>());
    }

    @Override
    public Collection<Integer> getSeeds() {
        return seeds;
    }

    @Override
    public long estimateSize() {
        return subscriberDataMap.values()
                .stream()
                .mapToLong(subscriberData -> {
                    final long bytesPerLong = 8;
                    final long valueBytes = bytesPerLong * subscriberData.getTopics().size();
                    // group bytes estimated by string length because group names should be ASCII
                    final long groupBytes = subscriberData.getGroup().map(String::length).orElse(0);
                    return valueBytes + groupBytes;
                })
                .sum();
    }

    @Override
    public LiteralUpdate export() {
        final Set<String> serializedGroupedTopics = new HashSet<>();
        subscriberDataMap.forEach((subscriber, data) -> {
            final Set<Long> topicHashes = data.getTopics()
                    .stream()
                    .map(this::hashAsLong)
                    .collect(Collectors.toSet());
            final Grouped<Long> groupedHashes = Grouped.of(data.getGroup().orElse(null), topicHashes);
            serializedGroupedTopics.add(groupedHashes.toJsonString());
        });
        return LiteralUpdate.withInserts(serializedGroupedTopics);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof CompressedSubscriptions) {
            final CompressedSubscriptions that = (CompressedSubscriptions) other;
            return seeds.equals(that.seeds) && super.equals(other);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(seeds, super.hashCode());
    }

}
