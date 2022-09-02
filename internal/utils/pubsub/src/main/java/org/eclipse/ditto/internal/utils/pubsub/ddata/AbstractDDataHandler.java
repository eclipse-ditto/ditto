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
package org.eclipse.ditto.internal.utils.pubsub.ddata;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

import org.eclipse.ditto.internal.utils.ddata.DistributedData;
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.ORMultiMapKey;
import akka.cluster.ddata.Replicator;
import akka.cluster.ddata.SelfUniqueAddress;

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
    public int getNumberOfShards() {
        return numberOfShards;
    }

    @Override
    public CompletionStage<Void> put(final K ownSubscriber, final T update,
            final Replicator.WriteConsistency writeConsistency) {

        if (update.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        } else {
            return update(getKey(selfUniqueAddress.uniqueAddress().address()), writeConsistency, initialMMap -> {
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
    public CompletionStage<Void> reset(final K ownSubscriber, final T topics,
            final Replicator.WriteConsistency writeConsistency) {

        return update(getKey(selfUniqueAddress.uniqueAddress().address()), writeConsistency,
                mmap -> mmap.put(selfUniqueAddress, ownSubscriber, topics.getInserts()));
    }

    @Override
    public CompletionStage<Void> removeSubscriber(final K subscriber,
            final Replicator.WriteConsistency writeConsistency) {
        return update(getKey(selfUniqueAddress.uniqueAddress().address()), writeConsistency, mmap -> mmap.remove(selfUniqueAddress, subscriber));
    }

    @Override
    public void receiveChanges(final ActorRef recipient) {
        IntStream.range(0, numberOfShards)
                .forEach(i -> replicator.tell(new Replicator.Subscribe<>(getKey(i), recipient), ActorRef.noSender()));
    }

    @Override
    public Key<ORMultiMap<K, S>> getKey(final int shardNumber) {
        if (shardNumber < 0) {
            throw new IllegalArgumentException("Negative shardNumber is not supported: " + shardNumber);
        }
        return ORMultiMapKey.create(String.format("%s-%d", topicType, shardNumber));
    }

    @Override
    protected ORMultiMap<K, S> getInitialValue() {
        return ORMultiMap.emptyWithValueDeltas();
    }

    @Override
    public CompletionStage<List<ORMultiMap<K, S>>> getAllShards(final Replicator.ReadConsistency consistency) {
        final var futures = IntStream.range(0, numberOfShards)
                .mapToObj(i -> get(getKey(i), consistency).toCompletableFuture())
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(unused -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .toList()
                );
    }

}
