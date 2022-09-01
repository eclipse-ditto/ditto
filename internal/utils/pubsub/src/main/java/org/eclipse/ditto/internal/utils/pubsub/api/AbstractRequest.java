/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.api;

import java.util.Collection;
import java.util.Set;

import akka.actor.ActorRef;

/**
 * Abstract base class for subscription requests.
 */
abstract class AbstractRequest implements Request {

    private final Set<String> topics;
    private final ActorRef subscriber;
    private final boolean acknowledge;

    protected AbstractRequest(final Collection<String> topics,
            final ActorRef subscriber,
            final boolean acknowledge) {

        this.topics = Set.copyOf(topics);
        this.subscriber = subscriber;
        this.acknowledge = acknowledge;
    }

    /**
     * @return topics in the subscription.
     */
    public Set<String> getTopics() {
        return topics;
    }

    /**
     * @return subscriber of the subscription.
     */
    public ActorRef getSubscriber() {
        return subscriber;
    }

    /**
     * @return whether acknowledgement is expected.
     */
    public boolean shouldAcknowledge() {
        return acknowledge;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "topics=" + topics +
                ", subscriber=" + subscriber +
                ", acknowledge=" + acknowledge +
                "]";
    }
}
