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
import java.util.Collections;
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
import scala.collection.JavaConverters;

/**
 * A distributed collection of Bloom filters of strings indexed by ActorRef.
 * The hash functions for all filter should be identical.
 */
public abstract class AbstractDDataHandler<S, T extends IndelUpdate<S, T>>
        extends DistributedData<ORMultiMap<ActorRef, S>>
        implements DDataReader<S>, DDataWriter<T> {

    private final String topicType;
    private final SelfUniqueAddress selfUniqueAddress;
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
    public abstract S approximate(final String topic);

    @Override
    public CompletionStage<Collection<ActorRef>> getSubscribers(final Collection<S> topic) {

        return get(Replicator.readLocal()).thenApply(optional -> {
            if (optional.isPresent()) {
                final ORMultiMap<ActorRef, S> mmap = optional.get();
                ddataMetrics.set((long) mmap.size());
                return JavaConverters.mapAsJavaMap(mmap.entries())
                        .entrySet()
                        .stream()
                        .filter(entry -> topic.stream().anyMatch(entry.getValue()::contains))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            } else {
                ddataMetrics.set(0L);
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletionStage<Void> removeAddress(final Address address,
            final Replicator.WriteConsistency writeConsistency) {
        return update(writeConsistency, mmap -> {
            ORMultiMap<ActorRef, S> result = mmap;
            for (final ActorRef subscriber : mmap.getEntries().keySet()) {
                if (subscriber.path().address().equals(address)) {
                    result = result.remove(selfUniqueAddress, subscriber);
                }
            }
            return result;
        });
    }

    @Override
    public CompletionStage<Void> put(final ActorRef ownSubscriber, final T topics,
            final Replicator.WriteConsistency writeConsistency) {

        if (topics.shouldReplaceAll()) {
            // complete replacement
            return update(writeConsistency, mmap -> mmap.put(selfUniqueAddress, ownSubscriber, topics.getInserts()));
        } else {
            // incremental update
            return update(writeConsistency, mmap -> {
                ORMultiMap<ActorRef, S> result = mmap;
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
    public CompletionStage<Void> removeSubscriber(final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency) {
        return update(writeConsistency, mmap -> mmap.remove(selfUniqueAddress, subscriber));
    }

    @Override
    protected Key<ORMultiMap<ActorRef, S>> getKey() {
        return ORMultiMapKey.create(topicType);
    }

    @Override
    protected ORMultiMap<ActorRef, S> getInitialValue() {
        return ORMultiMap.emptyWithValueDeltas();
    }
}
