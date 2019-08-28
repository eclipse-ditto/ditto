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

import akka.actor.ActorContext;

/**
 * Pub-sub factory for thing events.
 */
public final class ThingEventPubSubFactory extends AbstractPubSubFactory<ThingEvent> {

    private static final DDataProvider PROVIDER = DDataProvider.of("thing-event-aware");

    private ThingEventPubSubFactory(final ActorContext context, final PubSubTopicExtractor<ThingEvent> topicExtractor) {

        super(context, ThingEvent.class, topicExtractor, PROVIDER);
    }

    /**
     * Create a pubsub factory for thing events from an actor system and its shard region extractor.
     *
     * @param context context of the actor under which publisher and subscriber actors are created.
     * @param shardRegionExtractor the shard region extractor.
     * @return the thing event pub-sub factory.
     */
    public static ThingEventPubSubFactory of(final ActorContext context,
            final ShardRegionExtractor shardRegionExtractor) {

        return new ThingEventPubSubFactory(context, toTopicExtractor(shardRegionExtractor));
    }

    /**
     * Create a pubsub factory for thing events ignoring shard ID topics.
     *
     * @param context context of the actor under which publisher and subscriber actors are created.
     * @return the thing event pub-sub factory.
     */
    public static ThingEventPubSubFactory readSubjectsOnly(final ActorContext context) {
        return new ThingEventPubSubFactory(context, readSubjectOnlyExtractor());
    }

    /**
     * Create a pubsub factory for thing events ignoring read subject topics.
     *
     * @param context context of the actor under which publisher and subscriber actors are created.
     * @param numberOfShards the number of shards---must be identical between things and thing-updaters.
     * @return the thing event pug-sub factory.
     */
    public static ThingEventPubSubFactory shardIdOnly(final ActorContext context, final int numberOfShards) {

        final PubSubTopicExtractor<ThingEvent> topicExtractor =
                shardIdOnlyExtractor(ShardRegionExtractor.of(numberOfShards, context.system()));
        return new ThingEventPubSubFactory(context, topicExtractor);
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
