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

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.pubsub.DistributedAcks;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

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
     * @return future that completes or fails according to the acknowledgement.
     */
    CompletionStage<Void> subscribe(Collection<StreamingType> types, Collection<String> topics, ActorRef subscriber);

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
     * @return future that completes or fails according to the acknowledgement.
     */
    CompletionStage<Void> updateLiveSubscriptions(Collection<StreamingType> types, Collection<String> topics,
            ActorRef subscriber);

    /**
     * Remove a subscriber from the twin events channel only.
     *
     * @param subscriber whom to remove.
     * @param topics what were the subscribed topics.
     * @return future that completes or fails according to the acknowledgement.
     */
    CompletionStage<Void> removeTwinSubscriber(ActorRef subscriber, Collection<String> topics);

    /**
     * Declare acknowledgement labels for a subscriber.
     * Declared acknowledgement labels are globally unique for each subscriber.
     * When racing against another subscriber on another node, the future may still complete successfully,
     * but the subscriber losing the race will receive an {@code AcknowledgementLabelNotUniqueException} later.
     * This method will always return a failed future if a distributed data for declared labels is not provided.
     *
     * @param acknowledgementLabels the acknowledgement labels to declare.
     * @param subscriber the subscriber making the declaration.
     * @return a future that completes successfully when the initial declaration succeeds and fails if duplicate labels
     * are known. Subscribers losing a race against remote subscribers may receive an
     * {@code AcknowledgementLabelNotUniqueException} later.
     */
    CompletionStage<Void> declareAcknowledgementLabels(Collection<AcknowledgementLabel> acknowledgementLabels,
            ActorRef subscriber);

    /**
     * Relinquish any acknowledgement labels declared by a subscriber.
     *
     * @param subscriber the subscriber.
     */
    void removeAcknowledgementLabelDeclaration(ActorRef subscriber);

    /**
     * Create {@code DittoProtocolSub} for an actor system.
     *
     * @param context context of the actor under which the subscriber actors are started.
     * @param distributedAcks the distributed acks interface.
     * @return the {@code DittoProtocolSub}.
     */
    static DittoProtocolSub of(final ActorContext context, final DistributedAcks distributedAcks) {
        return DittoProtocolSubImpl.of(context, distributedAcks);
    }
}
