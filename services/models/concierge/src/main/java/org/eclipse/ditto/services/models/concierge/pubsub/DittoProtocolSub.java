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
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.pubsub.actors.SubUpdater;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Subscriptions for Ditto protocol channels.
 */
public interface DittoProtocolSub {

    /**
     * Subscribe for each streaming type the same collection of topics.
     *
     * @param types the streaming types.
     * @param topics the topics.
     * @param subscriber who is subscribing.
     * @return future that completes or fails according to the subscriptions.
     */
    CompletionStage<SubUpdater.Acknowledgement> subscribe(Collection<StreamingType> types,
            Collection<String> topics, ActorRef subscriber);

    /**
     * Remove a subscriber.
     *
     * @param subscriber who is unsubscribing.
     */
    void removeSubscriber(ActorRef subscriber);

    /**
     * Update streaming types of a subscriber.
     *
     * @param types the currently active streaming types.
     * @param topics the topics to unsubscribe from.
     * @param subscriber the subscriber.
     * @return future acknowledgement.
     */
    CompletionStage<SubUpdater.Acknowledgement> updateSubscription(Collection<StreamingType> types,
            Collection<String> topics,
            ActorRef subscriber);

    /**
     * Create {@code DittoProtocolSub} for an actor system.
     *
     * @param actorSystem the actor system.
     * @return the {@code DittoProtocolSub}.
     */
    static DittoProtocolSub of(final ActorSystem actorSystem) {
        return DittoProtocolSubImpl.of(actorSystem);
    }
}
