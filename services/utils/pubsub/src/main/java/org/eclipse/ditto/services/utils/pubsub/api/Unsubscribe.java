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
package org.eclipse.ditto.services.utils.pubsub.api;

import java.util.Set;

import akka.actor.ActorRef;
import akka.cluster.ddata.Replicator;

/**
 * Request to unsubscribe to topics.
 */
public final class Unsubscribe extends AbstractRequest {

    private Unsubscribe(final Set<String> topics,
            final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency,
            final boolean acknowledge) {
        super(topics, subscriber, writeConsistency, acknowledge);
    }

    /**
     * Create an "unsubscribe" request.
     *
     * @param topics the set of topics to subscribe.
     * @param subscriber who is subscribing.
     * @param writeConsistency with which write consistency should this subscription be updated.
     * @param acknowledge whether acknowledgement is desired.
     * @return the request.
     */
    public static Unsubscribe of(final Set<String> topics,
            final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency,
            final boolean acknowledge) {
        return new Unsubscribe(topics, subscriber, writeConsistency, acknowledge);
    }
}
