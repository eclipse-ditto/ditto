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

import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.pubsub.AbstractPubSubFactory;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.extractors.ReadSubjectExtractor;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;

/**
 * Pub-sub factory for one type of live signals.
 */
final class SingleLiveSignalPubSubFactory<T extends Signal> extends AbstractPubSubFactory<T> {

    /**
     * Cluster role interested in live signals.
     */
    public static final String CLUSTER_ROLE = "live-signal-aware";

    private SingleLiveSignalPubSubFactory(final ActorSystem actorSystem, final PubSubConfig config,
            final Class<T> messageClass, final String ddataKey) {

        super(actorSystem, CLUSTER_ROLE, messageClass, ddataKey, ReadSubjectExtractor.of(), config);
    }

    /**
     * Create a pubsub factory for live signals from an actor system and its shard region extractor.
     *
     * @param actorSystem the actor system.
     * @return the thing
     */
    public static <T extends Signal> SingleLiveSignalPubSubFactory<T> of(final ActorSystem actorSystem,
            final PubSubConfig pubSubConfig,
            final Class<T> signalClass,
            final StreamingType streamingType) {

        return new SingleLiveSignalPubSubFactory<>(actorSystem, pubSubConfig, signalClass,
                streamingType.getDistributedPubSubTopic());
    }
}
