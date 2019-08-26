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
package org.eclipse.ditto.services.models.things;

import java.util.Arrays;

import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.pubsub.AbstractPubSubFactory;
import org.eclipse.ditto.services.utils.pubsub.extractors.ConstantTopics;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;
import org.eclipse.ditto.services.utils.pubsub.extractors.ReadSubjectExtractor;
import org.eclipse.ditto.services.utils.pubsub.extractors.ShardIdExtractor;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ActorSystem;

/**
 * Pub-sub factory for thing events.
 */
public final class ThingEventPubSubFactory extends AbstractPubSubFactory<ThingEvent> {

    /**
     * Cluster role interested in thing events.
     */
    public static final String CLUSTER_ROLE = "thing-event-aware";

    private ThingEventPubSubFactory(final ActorSystem actorSystem,
            final PubSubTopicExtractor<ThingEvent> topicExtractor) {
        super(actorSystem, CLUSTER_ROLE, ThingEvent.class, CLUSTER_ROLE, topicExtractor);
    }

    /**
     * Create a pubsub factory for thing events from an actor system and its shard region extractor.
     *
     * @param actorSystem the actor system.
     * @param shardRegionExtractor the shard region extractor.
     * @return the thing event pub-sub factory.
     */
    public static ThingEventPubSubFactory of(final ActorSystem actorSystem,
            final ShardRegionExtractor shardRegionExtractor) {

        return new ThingEventPubSubFactory(actorSystem, toTopicExtractor(shardRegionExtractor));
    }

    /**
     * Create a pubsub factory for thing events ignoring shard ID topics.
     *
     * @param actorSystem the actor system.
     * @return the thing event pub-sub factory.
     */
    public static ThingEventPubSubFactory readSubjectsOnly(final ActorSystem actorSystem) {
        return new ThingEventPubSubFactory(actorSystem, readSubjectOnlyExtractor());
    }

    /**
     * Create a pubsub factory for thing events ignoring read subject topics.
     *
     * @param actorSystem the actor system.
     * @param numberOfShards the number of shards---must be identical between things and thing-updaters.
     * @return the thing event pug-sub factory.
     */
    public static ThingEventPubSubFactory shardIdOnly(final ActorSystem actorSystem, final int numberOfShards) {

        final PubSubTopicExtractor<ThingEvent> topicExtractor =
                shardIdOnlyExtractor(ShardRegionExtractor.of(numberOfShards, actorSystem));
        return new ThingEventPubSubFactory(actorSystem, topicExtractor);
    }

    private static PubSubTopicExtractor<ThingEvent> readSubjectOnlyExtractor() {
        return ReadSubjectExtractor.<ThingEvent>of().with(ConstantTopics.of(ThingEvent.TYPE_PREFIX));
    }

    private static PubSubTopicExtractor<ThingEvent> shardIdOnlyExtractor(final ShardRegionExtractor extractor) {
        return ShardIdExtractor.of(extractor);
    }

    private static PubSubTopicExtractor<ThingEvent> toTopicExtractor(final ShardRegionExtractor shardRegionExtractor) {
        return ReadSubjectExtractor.<ThingEvent>of().with(
                Arrays.asList(ConstantTopics.of(ThingEvent.TYPE_PREFIX), shardIdOnlyExtractor(shardRegionExtractor)));
    }
}
