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

import org.eclipse.ditto.services.utils.pubsub.actors.Publisher;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorRef;

/**
 * Package-private implementation of {@link org.eclipse.ditto.services.utils.pubsub.DistributedPub}.
 */
final class DistributedPubImpl<T> implements DistributedPub<T> {

    private final ActorRef pubSupervisor;
    private final PubSubTopicExtractor<T> topicExtractor;

    DistributedPubImpl(final ActorRef pubSupervisor, final PubSubTopicExtractor<T> topicExtractor) {
        this.pubSupervisor = pubSupervisor;
        this.topicExtractor = topicExtractor;
    }

    @Override
    public ActorRef getPublisher() {
        return pubSupervisor;
    }

    @Override
    public Object wrapForPublication(final T message) {
        return Publisher.Publish.of(topicExtractor.getTopics(message), message);
    }
}
