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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.Props;

/**
 * Actor that distributes messages to local subscribers.
 *
 * @param <T> type of messages.
 */
final class SubSubscriber<T extends Signal<?>> extends AbstractSubscriber<T> {

    private SubSubscriber(final Class<T> messageClass, final PubSubTopicExtractor<T> topicExtractor,
            final AckExtractor<T> ackExtractor, final DistributedAcks distributedAcks) {
        super(messageClass, topicExtractor, ackExtractor, distributedAcks);
    }

    static <T> Props props(final Class<T> messageClass, final PubSubTopicExtractor<T> topicExtractor,
            final AckExtractor<T> ackExtractor, final DistributedAcks distributedAcks) {
        return Props.create(SubSubscriber.class, messageClass, topicExtractor, ackExtractor, distributedAcks);
    }
}
