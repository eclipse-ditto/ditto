/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsubthings;

import java.util.Arrays;

import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.pubsub.AbstractPubSubFactory;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.ConstantTopics;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.ReadSubjectExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.ShardIdExtractor;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorContext;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;

/**
 * Pub-sub factory for thing events.
 */
public final class ThingEventPubSubFactory extends AbstractPubSubFactory<ThingEvent<?>> {

    private static final AckExtractor<ThingEvent<?>> ACK_EXTRACTOR =
            AckExtractor.of(ThingEvent::getEntityId, ThingEvent::getDittoHeaders);

    private static final DDataProvider PROVIDER = DDataProvider.of("thing-event-aware");

    @SuppressWarnings({"unchecked"})
    private ThingEventPubSubFactory(final ActorRefFactory actorRefFactory,
            final ActorSystem actorSystem,
            final PubSubTopicExtractor<ThingEvent<?>> topicExtractor,
            final DistributedAcks distributedAcks) {

        super(actorRefFactory, actorSystem, (Class<ThingEvent<?>>) (Object) ThingEvent.class, topicExtractor, PROVIDER,
                ACK_EXTRACTOR, distributedAcks);
    }

    /**
     * Create a pubsub factory for thing events from an actor system and its shard region extractor.
     *
     * @param context context of the actor under which publisher and subscriber actors are created.
     * @param shardRegionExtractor the shard region extractor.
     * @param distributedAcks the distributed acks interface.
     * @return the thing event pub-sub factory.
     */
    public static ThingEventPubSubFactory of(final ActorContext context,
            final ShardRegionExtractor shardRegionExtractor,
            final DistributedAcks distributedAcks) {

        return new ThingEventPubSubFactory(context, context.system(), toTopicExtractor(shardRegionExtractor),
                distributedAcks);
    }

    /**
     * Create a pubsub factory for thing events ignoring shard ID topics.
     *
     * @param system the actor system.
     * @param distributedAcks the distributed acks interface.
     * @return the thing event pub-sub factory.
     */
    public static ThingEventPubSubFactory readSubjectsOnly(final ActorSystem system,
            final DistributedAcks distributedAcks) {
        return new ThingEventPubSubFactory(system, system, readSubjectOnlyExtractor(), distributedAcks);
    }

    private static PubSubTopicExtractor<ThingEvent<?>> readSubjectOnlyExtractor() {
        return ReadSubjectExtractor.<ThingEvent<?>>of().with(ConstantTopics.of(ThingEvent.TYPE_PREFIX));
    }

    private static PubSubTopicExtractor<ThingEvent<?>> shardIdOnlyExtractor(final ShardRegionExtractor extractor) {
        return ShardIdExtractor.of(extractor);
    }

    private static PubSubTopicExtractor<ThingEvent<?>> toTopicExtractor(
            final ShardRegionExtractor shardRegionExtractor) {
        return ReadSubjectExtractor.<ThingEvent<?>>of().with(
                Arrays.asList(ConstantTopics.of(ThingEvent.TYPE_PREFIX), shardIdOnlyExtractor(shardRegionExtractor)));
    }
}
