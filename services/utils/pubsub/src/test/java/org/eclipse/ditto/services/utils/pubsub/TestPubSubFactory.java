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

import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.services.utils.pubsub.ddata.Hashes;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

/**
 * Pub-sub factory for tests. Messages are strings. Topics of a message are its prefixes.
 */
public final class TestPubSubFactory extends AbstractPubSubFactory<String> implements Hashes {

    private static final DDataProvider PROVIDER = DDataProvider.of("dc-default");

    private final Collection<Integer> seeds;

    private TestPubSubFactory(final ActorContext context, final Class<String> messageClass,
            final PubSubTopicExtractor<String> topicExtractor) {
        super(context, messageClass, topicExtractor, PROVIDER);
        final PubSubConfig config = PubSubConfig.of(context.system().settings().config().getConfig("ditto.pubsub"));
        seeds = Hashes.digestStringsToIntegers(config.getSeed(), config.getHashFamilySize());
    }

    static TestPubSubFactory of(final ActorContext context) {
        return new TestPubSubFactory(context, String.class, TestPubSubFactory::getPrefixes);
    }

    /**
     * @return subscribers of a topic in the distributed data.
     */
    CompletionStage<Collection<ActorRef>> getSubscribers(final String topic) {
        return getSubscribers(Collections.singleton(topic), ddata.getReader());
    }

    /**
     * Retrieve subscribers of a collection of topics from the distributed data.
     * Useful for circumventing lackluster existential type implementation when the reader type parameter isn't known.
     *
     * @param topics the topics.
     * @return subscribers of those topics in the distributed data.
     */
    private static <T> CompletionStage<Collection<ActorRef>> getSubscribers(
            final Collection<String> topics, final DDataReader<T> reader) {
        return reader.getSubscribers(topics.stream().map(reader::approximate).collect(Collectors.toSet()));
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
