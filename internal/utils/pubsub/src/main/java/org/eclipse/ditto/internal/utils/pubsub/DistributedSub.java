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
package org.eclipse.ditto.internal.utils.pubsub;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.api.SubAck;

import akka.actor.ActorRef;

/**
 * Access point for Ditto pub-sub subscribers.
 */
public interface DistributedSub {

    /**
     * Subscribe for a collection of topics with a local topic filter.
     *
     * @param topics the topics.
     * @param subscriber who is subscribing.
     * @param filter a local topic filter.
     * @param group the subscriber's group, if any.
     * @param resubscribe whether this is a resubscription.
     * @return a future that completes after subscription becomes effective on all nodes.
     */
    CompletionStage<SubAck> subscribeWithFilterAndGroup(Collection<String> topics,
            ActorRef subscriber, @Nullable Predicate<Collection<String>> filter, @Nullable String group,
            final boolean resubscribe);

    /**
     * Unsubscribe for a collection of topics.
     *
     * @param topics the topics.
     * @param subscriber who is unsubscribing.
     * @return a future that completes when the unsubscriber stops receiving messages on the given topics.
     */
    CompletionStage<SubAck> unsubscribeWithAck(Collection<String> topics, ActorRef subscriber);

    /**
     * Subscribe for topics without waiting for acknowledgement.
     *
     * @param topics the topics.
     * @param subscriber who is subscribing.
     */
    void subscribeWithoutAck(Collection<String> topics, ActorRef subscriber);

    /**
     * Unsubscribe for topics without waiting for acknowledgement.
     *
     * @param topics the topics.
     * @param subscriber who is unsubscribing.
     */
    void unsubscribeWithoutAck(Collection<String> topics, ActorRef subscriber);

    /**
     * Remove a subscriber without waiting for acknowledgement.
     *
     * @param subscriber who is being removed.
     */
    void removeSubscriber(ActorRef subscriber);

    /**
     * Create subscription access from an already-started sub-supervisor and a distributed data config.
     *
     * @param config the distributed-data config.
     * @param subSupervisor the sub-supervisor.
     * @return the subscription access.
     */
    static DistributedSub of(final DistributedDataConfig config, final ActorRef subSupervisor) {
        return new DistributedSubImpl(config, subSupervisor);
    }
}
