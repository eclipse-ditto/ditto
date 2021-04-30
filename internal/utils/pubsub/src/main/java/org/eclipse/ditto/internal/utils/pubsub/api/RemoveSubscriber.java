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

import java.util.Collections;

import akka.actor.ActorRef;
import akka.cluster.ddata.Replicator;

/**
 * Request to remove a subscriber.
 */
public final class RemoveSubscriber extends AbstractRequest {

    private RemoveSubscriber(final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency,
            final boolean acknowledge) {
        super(Collections.emptySet(), subscriber, writeConsistency, acknowledge);
    }

    /**
     * Create an "unsubscribe" request.
     *
     * @param subscriber who is subscribing.
     * @param writeConsistency with which write consistency should this subscription be updated.
     * @param acknowledge whether acknowledgement is desired.
     * @return the request.
     */
    public static RemoveSubscriber of(final ActorRef subscriber,
            final Replicator.WriteConsistency writeConsistency,
            final boolean acknowledge) {
        return new RemoveSubscriber(subscriber, writeConsistency, acknowledge);
    }

}
