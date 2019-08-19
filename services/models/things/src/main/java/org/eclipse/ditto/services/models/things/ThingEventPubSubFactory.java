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

import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.pubsub.AbstractPubSubFactory;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
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

    private ThingEventPubSubFactory(final ActorSystem actorSystem, final ShardRegionExtractor shardRegionExtractor,
            final PubSubConfig config) {
        super(actorSystem, CLUSTER_ROLE, ThingEvent.class, toTopicExtractor(shardRegionExtractor), config);
    }

    /**
     * Create a pubsub factory for thing events from an actor system and its shard region extractor.
     *
     * @param actorSystem the actor system.
     * @param shardRegionExtractor the shard region extractor.
     * @return the thing
     */
    public static ThingEventPubSubFactory of(final ActorSystem actorSystem,
            final ShardRegionExtractor shardRegionExtractor) {

        final PubSubConfig config = PubSubConfig.forActorSystem(actorSystem);
        return new ThingEventPubSubFactory(actorSystem, shardRegionExtractor, config);
    }

    private static PubSubTopicExtractor<ThingEvent> toTopicExtractor(final ShardRegionExtractor shardRegionExtractor) {
        return ReadSubjectExtractor.<ThingEvent>of().with(ShardIdExtractor.of(shardRegionExtractor));
    }
}
