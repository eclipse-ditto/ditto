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
package org.eclipse.ditto.services.utils.pubsub;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.services.utils.pubsub.bloomfilter.Hashes;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Pub-sub factory for tests. Messages are strings. Topics of a message are its prefixes.
 */
public final class TestPubSubFactory extends AbstractPubSubFactory<String> implements Hashes {

    private final Collection<Integer> seeds;

    private TestPubSubFactory(final ActorSystem actorSystem, final String clusterRole,
            final Class<String> messageClass,
            final PubSubTopicExtractor<String> topicExtractor,
            final PubSubConfig config) {
        super(actorSystem, clusterRole, messageClass, topicExtractor, config);
        seeds = Hashes.digestStringsToIntegers(config.getSeed(), config.getHashFamilySize());
    }

    /**
     * Create a test pub-sub factory from an actor system in a cluster.
     *
     * @param actorSystem the actor system.
     * @return the pub-sub factory.
     */
    public static TestPubSubFactory of(final ActorSystem actorSystem) {
        final PubSubConfig pubSubConfig =
                PubSubConfig.of(actorSystem.settings().config().getConfig("test-pubsub-factory"));
        return new TestPubSubFactory(actorSystem, "dc-default", String.class,
                TestPubSubFactory::getPrefixes, pubSubConfig);
    }

    /**
     * @return subscribers of a topic in the distributed data.
     */
    public CompletionStage<Collection<ActorRef>> getSubscribers(final String topic) {
        return topicBloomFilters.getSubscribers(Collections.singletonList(getHashes(topic)));
    }

    @Override
    public Collection<Integer> getSeeds() {
        return seeds;
    }

    private static Collection<String> getPrefixes(final String string) {
        return IntStream.range(0, string.length())
                .mapToObj(i -> string.substring(0, i + 1))
                .collect(Collectors.toList());
    }
}
