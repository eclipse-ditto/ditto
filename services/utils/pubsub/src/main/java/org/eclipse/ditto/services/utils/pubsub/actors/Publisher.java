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
package org.eclipse.ditto.services.utils.pubsub.actors;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.Hashes;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.TopicBloomFiltersReader;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Publishes messages according to topic Bloom filters.
 */
public final class Publisher extends AbstractActor implements Hashes {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "publisher";

    private DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Collection<Integer> seeds;
    private final TopicBloomFiltersReader topicBloomFiltersReader;
    private final ActorRef pubSubUpdater;

    private final Counter messageCounter = DittoMetrics.counter("pubsub-published-messages");
    private final Counter topicCounter = DittoMetrics.counter("pubsub-published-topics");

    private Publisher(final Collection<Integer> seeds, final TopicBloomFiltersReader topicBloomFiltersReader,
            final ActorRef pubSubUpdater) {
        this.seeds = seeds;
        this.topicBloomFiltersReader = topicBloomFiltersReader;
        this.pubSubUpdater = pubSubUpdater;
    }

    /**
     * Create Props for this actor.
     *
     * @param seeds seeds of the family of hash functions for Bloom filters.
     * @param topicBloomFiltersReader reader of remote subscriptions.
     * @param pubSubUpdater updater of remote subscriptions.
     * @return a Props object.
     */
    public static Props props(final Collection<Integer> seeds, final TopicBloomFiltersReader topicBloomFiltersReader,
            final ActorRef pubSubUpdater) {

        return Props.create(Publisher.class, seeds, topicBloomFiltersReader, pubSubUpdater);
    }

    @Override
    public Collection<Integer> getSeeds() {
        return seeds;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Publish.class, this::publish)
                .matchAny(this::logUnhandled)
                .build();
    }

    private void publish(final Publish publish) {
        messageCounter.increment();
        topicCounter.increment(publish.getTopics().size());
        final List<List<Integer>> hashes =
                publish.getTopics().stream().map(this::getHashes).collect(Collectors.toList());
        final Object message = publish.getMessage();
        final ActorRef sender = getSender();
        topicBloomFiltersReader.getSubscribers(hashes)
                .thenAccept(subscribers -> subscribers.forEach(subscriber -> subscriber.tell(message, sender)))
                .exceptionally(e -> {
                    log.error(e, "Failed: <{}>", publish);
                    return null;
                });
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }

    /**
     * Command for the publisher to publish a message.
     * Only the message is sent across the cluster.
     */
    public static final class Publish {

        private final Collection<String> topics;

        private final Object message;

        private Publish(final Collection<String> topics, final Object message) {
            this.topics = topics;
            this.message = message;
        }

        /**
         * Create a publish message for the publisher.
         *
         * @param topics the topics to publish at.
         * @param message the message to publish.
         * @return a publish message.
         */
        public static Publish of(final Collection<String> topics, final Object message) {
            return new Publish(topics, message);
        }

        /**
         * Get the collection of topics the message is published to.
         *
         * @return the collection of topics.
         */
        public Collection<String> getTopics() {
            return topics;
        }

        /**
         * Get the message to publish.
         *
         * @return the message.
         */
        public Object getMessage() {
            return message;
        }
    }
}
