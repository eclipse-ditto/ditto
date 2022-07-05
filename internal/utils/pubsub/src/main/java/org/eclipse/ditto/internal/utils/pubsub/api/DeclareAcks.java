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

import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import akka.actor.ActorRef;

/**
 * Request to declare a set of acknowledgement labels with an optional group.
 */
public final class DeclareAcks implements AckRequest {

    private final ActorRef subscriber;
    @Nullable private final String group;
    private final Set<String> ackLabels;

    private DeclareAcks(final ActorRef subscriber, @Nullable final String group, final Set<String> ackLabels) {
        this.subscriber = subscriber;
        this.group = group;
        this.ackLabels = ackLabels;
    }

    /**
     * Create a request to declare acknowledgement labels.
     *
     * @param subscriber the subscriber declaring the ack labels.
     * @param group the group of the subscriber if it has one, or null otherwise.
     * @param ackLabels the set of acknowledgement labels being declared - may be empty.
     * @return the request.
     */
    public static AckRequest of(final ActorRef subscriber, @Nullable final String group, final Set<String> ackLabels) {
        return new DeclareAcks(subscriber, group, ackLabels);
    }

    /**
     * Retrieve the subscriber.
     *
     * @return the subscriber.
     */
    public ActorRef getSubscriber() {
        return subscriber;
    }

    /**
     * Retrieve the group.
     *
     * @return the optional group.
     */
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    /**
     * Retrieve the acknowledgement labels.
     *
     * @return the acknowledgement labels.
     */
    public Set<String> getAckLabels() {
        return ackLabels;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[subscriber=" + subscriber +
                ",group=" + group +
                ",ackLabels=" + ackLabels +
                "]";
    }
}
