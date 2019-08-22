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
package org.eclipse.ditto.services.utils.pubsub.ddata.compressed;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.ddata.DistributedData;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfigReader;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.Hashes;

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
import akka.util.ByteString;
import scala.collection.JavaConverters;

/**
 * A distributed collection of Bloom filters of strings indexed by ActorRef.
 * The hash functions for all filter should be identical.
 */
public final class CompressedDData extends DistributedData<ORMultiMap<ActorRef, ByteString>>
        implements DDataReader<ByteString>, DDataWriter<CompressedUpdate>, Hashes {

    private final String topicType;
    private final SelfUniqueAddress selfUniqueAddress;
    private final List<Integer> seeds;

    private final Gauge ddataMetrics = DittoMetrics.gauge("pubsub-ddata-entries");

    private CompressedDData(final DistributedDataConfigReader configReader,
            final ActorRefFactory actorRefFactory,
            final ActorSystem actorSystem,
            final Executor ddataExecutor,
            final String topicType,
            final List<Integer> seeds) {
        super(configReader, actorRefFactory, ddataExecutor);
        this.topicType = topicType;
        this.selfUniqueAddress = SelfUniqueAddress.apply(Cluster.get(actorSystem).selfUniqueAddress());
        this.seeds = seeds;
    }

    /**
     * Start distributed-data replicator for compressed topics under an actor system's user guardian using the default
     * dispatcher.
     *
     * @param system the actor system.
     * @param ddataConfig the distributed data config.
     * @param topicType the type of messages, typically the canonical name of the message class.
     * @param pubSubConfig the pub-sub config.
     * @return access to the distributed data.
     */
    public static CompressedDData of(final ActorSystem system, final DistributedDataConfigReader ddataConfig,
            final String topicType, final PubSubConfig pubSubConfig) {

        final List<Integer> seeds =
                Hashes.digestStringsToIntegers(pubSubConfig.getSeed(), pubSubConfig.getHashFamilySize());

        return new CompressedDData(ddataConfig, system, system, system.dispatcher(), topicType, seeds);
    }

    @Override
    public List<Integer> getSeeds() {
        return seeds;
    }

    @Override
    public CompletionStage<Collection<ActorRef>> getSubscribers(final Collection<ByteString> topic) {

        return get(Replicator.readLocal()).thenApply(optional -> {
            if (optional.isPresent()) {
                final ORMultiMap<ActorRef, ByteString> mmap = optional.get();
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

    /**
     * Lossy-compress a topic into a ByteString consisting of hash codes from the family of hash functions.
     *
     * @param topic the topic.
     * @return the compressed topic.
     */
    @Override
    public ByteString approximate(final String topic) {
        @SuppressWarnings("unchecked")
        // force-casting to List<Object> to interface with covariant Scala collection
        final List<Object> hashes = (List<Object>) (Object) getHashes(topic);
        return ByteString.fromInts(JavaConverters.asScalaBuffer(hashes).toSeq());
    }

    @Override
    public CompletionStage<Void> removeAddress(final Address address,
            final Replicator.WriteConsistency writeConsistency) {
        return update(writeConsistency, mmap -> {
            ORMultiMap<ActorRef, ByteString> result = mmap;
            for (final ActorRef subscriber : mmap.getEntries().keySet()) {
                if (subscriber.path().address().equals(address)) {
                    result = result.remove(selfUniqueAddress, subscriber);
                }
            }
            return result;
        });
    }

    @Override
    public CompletionStage<Void> put(final ActorRef ownSubscriber, final CompressedUpdate topics,
            final Replicator.WriteConsistency writeConsistency) {

        return update(writeConsistency, mmap -> {
            ORMultiMap<ActorRef, ByteString> result = mmap;
            for (final ByteString inserted : topics.getInserts()) {
                result = result.addBinding(selfUniqueAddress, ownSubscriber, inserted);
            }
            for (final ByteString deleted : topics.getDeletes()) {
                result = result.removeBinding(selfUniqueAddress, ownSubscriber, deleted);
            }
            return result;
        });
    }

    @Override
    public CompletionStage<Void> removeSubscriber(final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency) {
        return update(writeConsistency, mmap -> mmap.remove(selfUniqueAddress, subscriber));
    }

    @Override
    protected Key<ORMultiMap<ActorRef, ByteString>> getKey() {
        return ORMultiMapKey.create(topicType);
    }

    @Override
    protected ORMultiMap<ActorRef, ByteString> getInitialValue() {
        return ORMultiMap.emptyWithValueDeltas();
    }
}
