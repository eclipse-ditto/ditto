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

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.AbstractDDataHandler;
import org.eclipse.ditto.internal.utils.pubsub.ddata.Hashes;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.Replicator;

/**
 * A distributed collection of hashes of strings indexed by ActorRef.
 * The hash functions for all filter should be identical.
 */
public final class CompressedDDataHandler extends AbstractDDataHandler<ActorRef, String, LiteralUpdate>
        implements Hashes {

    private final List<Integer> seeds;

    private CompressedDDataHandler(final DistributedDataConfig config,
            final ActorRefFactory actorRefFactory,
            final ActorSystem actorSystem,
            final Executor ddataExecutor,
            final String topicType,
            final List<Integer> seeds) {
        super(config, actorRefFactory, actorSystem, ddataExecutor, topicType);
        this.seeds = seeds;
    }

    /**
     * Start distributed-data replicator for compressed topics under an actor system's user guardian using the default
     * dispatcher.
     *
     * @param system the actor system.
     * @param ddataConfig the distributed data config.
     * @param topicType the type of messages, typically "message-type-name-aware".
     * @param pubSubConfig the pub-sub config.
     * @return access to the distributed data.
     */
    public static CompressedDDataHandler create(final ActorSystem system, final DistributedDataConfig ddataConfig,
            final String topicType, final PubSubConfig pubSubConfig) {

        final List<Integer> seeds =
                Hashes.digestStringsToIntegers(pubSubConfig.getSeed(), HASH_FAMILY_SIZE);

        return new CompressedDDataHandler(ddataConfig, system, system, system.dispatcher(), topicType, seeds);
    }

    @Override
    public List<Integer> getSeeds() {
        return seeds;
    }

    /**
     * Lossy-compress a topic into a ByteString consisting of hash codes from the family of hash functions.
     *
     * @param topic the topic.
     * @return the compressed topic.
     */
    @Override
    public long approximate(final String topic) {
        return hashAsLong(topic);
    }

    @Override
    public CompletionStage<Void> removeAddress(final Address address,
            final Replicator.WriteConsistency writeConsistency) {
        return update(getKey(address), writeConsistency, mmap -> {
            ORMultiMap<ActorRef, String> result = mmap;
            for (final ActorRef subscriber : mmap.getEntries().keySet()) {
                if (subscriber.path().address().equals(address)) {
                    result = result.remove(selfUniqueAddress, subscriber);
                }
            }
            return result;
        });
    }

}
