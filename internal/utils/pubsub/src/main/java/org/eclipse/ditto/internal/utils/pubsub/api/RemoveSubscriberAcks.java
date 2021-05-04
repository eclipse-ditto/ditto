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

import akka.actor.ActorRef;

/**
 * Remove declared acknowledgement labels of a subscriber.
 */
public final class RemoveSubscriberAcks implements AckRequest {

    private final ActorRef subscriber;

    private RemoveSubscriberAcks(final ActorRef subscriber) {
        this.subscriber = subscriber;
    }

    /**
     * Create a request to remove declared acknowledgement labels of a subscriber.
     *
     * @param subscriber the subscriber.
     * @return the request.
     */
    public static AckRequest of(final ActorRef subscriber) {
        return new RemoveSubscriberAcks(subscriber);
    }

    /**
     * Retrieve the subscriber.
     *
     * @return the subscriber.
     */
    public ActorRef getSubscriber() {
        return subscriber;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[subscriber=" + subscriber +
                "]";
    }
}
