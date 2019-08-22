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
package org.eclipse.ditto.services.models.concierge.pubsub;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.pubsub.AbstractPubSubFactory;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;
import org.eclipse.ditto.services.utils.pubsub.extractors.ReadSubjectExtractor;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;

/**
 * Pub-sub factory for one type of live signals.
 */
final class LiveAndTwinSignalPubSubFactory<T extends Signal> extends AbstractPubSubFactory<T> {

    /**
     * Cluster role interested in live signals.
     */
    public static final String CLUSTER_ROLE = "live-and-twin-thing-aware";

    private LiveAndTwinSignalPubSubFactory(final ActorSystem actorSystem, final PubSubConfig config,
            final Class<T> messageClass, final PubSubTopicExtractor<T> topicExtractor) {

        super(actorSystem, CLUSTER_ROLE, messageClass, CLUSTER_ROLE, topicExtractor, config);
    }

    /**
     * Create a pubsub factory for live signals from an actor system and its shard region extractor.
     *
     * @param actorSystem the actor system.
     * @return the thing
     */
    public static <T extends Signal> LiveAndTwinSignalPubSubFactory<T> of(final ActorSystem actorSystem,
            final PubSubConfig pubSubConfig,
            final Class<T> signalClass,
            final PubSubTopicExtractor<T> topicExtractor) {

        return new LiveAndTwinSignalPubSubFactory<>(actorSystem, pubSubConfig, signalClass, topicExtractor);
    }

    private static Collection<String> getStreamingTypeTopic(final Signal signal) {
        return StreamingType.fromSignal(signal)
                .map(StreamingType::getDistributedPubSubTopic)
                .map(Collections::singleton)
                .orElse(Collections.emptySet());
    }

    static PubSubTopicExtractor<Signal> topicExtractor() {
        return ReadSubjectExtractor.<Signal>of().with(LiveAndTwinSignalPubSubFactory::getStreamingTypeTopic);
    }
}
