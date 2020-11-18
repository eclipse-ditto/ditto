/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.ddata;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.ddata.DistributedData;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.ORMultiMapKey;
import akka.cluster.ddata.Replicator;
import akka.cluster.ddata.SelfUniqueAddress;
import scala.jdk.javaapi.CollectionConverters;

/**
 * A distributed collection of approximations of strings indexed by keys like ActorRef.
 */
public abstract class AbstractDDataHandler<K, S, T extends IndelUpdate<S, T>>
        extends DistributedData<ORMultiMap<K, S>>
        implements DDataReader<K, S>, DDataWriter<K, T> {

    protected final SelfUniqueAddress selfUniqueAddress;

    private final String topicType;
    private final Gauge ddataMetrics;

    protected AbstractDDataHandler(final DistributedDataConfig config,
            final ActorRefFactory actorRefFactory,
            final ActorSystem actorSystem,
            final Executor ddataExecutor,
            final String topicType) {
        super(config, actorRefFactory, ddataExecutor);
        this.topicType = topicType;
        this.selfUniqueAddress = SelfUniqueAddress.apply(Cluster.get(actorSystem).selfUniqueAddress());
        ddataMetrics = DittoMetrics.gauge("pubsub-ddata-entries").tag("topic", topicType);
    }

    @Override
    public abstract CompletionStage<Void> removeAddress(Address address, Replicator.WriteConsistency writeConsistency);

    @Override
    public abstract S approximate(final String topic);

    @Override
    public Collection<K> getSubscribers(final Map<K, scala.collection.immutable.Set<S>> mmap,
            final Collection<S> topic) {
        return mmap.entrySet()
                .stream()
                .filter(entry -> topic.stream().anyMatch(entry.getValue()::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public CompletionStage<Collection<K>> getSubscribers(final Collection<S> topic) {
        return read().thenApply(mmap -> getSubscribers(mmap, topic));
    }

    @Override
    public CompletionStage<Map<K, scala.collection.immutable.Set<S>>> read(
            final Replicator.ReadConsistency readConsistency) {

        return get(readConsistency).thenApply(optional -> {
            if (optional.isPresent()) {
                final ORMultiMap<K, S> mmap = optional.get();
                ddataMetrics.set((long) mmap.size());
                return CollectionConverters.asJava(mmap.entries());
            } else {
                ddataMetrics.set(0L);
                return Map.of();
            }
        });
    }

    @Override
    public CompletionStage<Void> put(final K ownSubscriber, final T topics,
            final Replicator.WriteConsistency writeConsistency) {

        if (topics.shouldReplaceAll()) {
            // complete replacement
            return update(writeConsistency, mmap -> mmap.put(selfUniqueAddress, ownSubscriber, topics.getInserts()));
        } else {
            // incremental update
            return update(writeConsistency, mmap -> {
                ORMultiMap<K, S> result = mmap;
                for (final S inserted : topics.getInserts()) {
                    result = result.addBinding(selfUniqueAddress, ownSubscriber, inserted);
                }
                for (final S deleted : topics.getDeletes()) {
                    result = result.removeBinding(selfUniqueAddress, ownSubscriber, deleted);
                }
                return result;
            });
        }
    }

    @Override
    public CompletionStage<Void> removeSubscriber(final K subscriber,
            final Replicator.WriteConsistency writeConsistency) {
        return update(writeConsistency, mmap -> mmap.remove(selfUniqueAddress, subscriber));
    }

    @Override
    public void receiveChanges(final ActorRef recipient) {
        replicator.tell(new Replicator.Subscribe<>(getKey(), recipient), ActorRef.noSender());
    }

    @Override
    public Key<ORMultiMap<K, S>> getKey() {
        return ORMultiMapKey.create(topicType);
    }

    @Override
    protected ORMultiMap<K, S> getInitialValue() {
        return ORMultiMap.emptyWithValueDeltas();
    }
}
