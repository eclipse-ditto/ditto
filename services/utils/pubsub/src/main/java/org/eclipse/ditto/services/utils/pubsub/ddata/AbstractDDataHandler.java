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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.services.utils.ddata.DistributedData;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
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
public abstract class AbstractDDataHandler<K, S, T extends DDataUpdate<S>>
        extends DistributedData<ORMultiMap<K, S>>
        implements DDataReader<K, S>, DDataWriter<K, T> {

    protected final SelfUniqueAddress selfUniqueAddress;

    private final String topicType;

    protected AbstractDDataHandler(final DistributedDataConfig config,
            final ActorRefFactory actorRefFactory,
            final ActorSystem actorSystem,
            final Executor ddataExecutor,
            final String topicType) {
        super(config, actorRefFactory, ddataExecutor);
        this.topicType = topicType;
        this.selfUniqueAddress = SelfUniqueAddress.apply(Cluster.get(actorSystem).selfUniqueAddress());
    }

    @Override
    public CompletionStage<Map<K, scala.collection.immutable.Set<S>>> read(
            final Replicator.ReadConsistency readConsistency) {

        return get(readConsistency).thenApply(optional -> {
            if (optional.isPresent()) {
                final ORMultiMap<K, S> mmap = optional.get();
                return CollectionConverters.asJava(mmap.entries());
            } else {
                return Map.of();
            }
        });
    }

    @Override
    public CompletionStage<Void> put(final K ownSubscriber, final T update,
            final Replicator.WriteConsistency writeConsistency) {

        if (update.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        } else {
            return update(writeConsistency, initialMMap -> {
                ORMultiMap<K, S> mmap = initialMMap;
                for (final S delete : update.getDeletes()) {
                    mmap = mmap.removeBinding(selfUniqueAddress, ownSubscriber, delete);
                }
                for (final S insert : update.getInserts()) {
                    mmap = mmap.addBinding(selfUniqueAddress, ownSubscriber, insert);
                }
                return mmap;
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
