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

import akka.actor.ActorRef;

/**
 * Request to unsubscribe to topics.
 */
public final class Unsubscribe extends AbstractRequest {

    private Unsubscribe(final Collection<String> topics, final ActorRef subscriber, final boolean acknowledge) {
        super(topics, subscriber, acknowledge);
    }

    /**
     * Create an "unsubscribe" request.
     *
     * @param topics the set of topics to subscribe.
     * @param subscriber who is subscribing.
     * @param acknowledge whether acknowledgement is desired.
     * @return the request.
     */
    public static Unsubscribe of(final Collection<String> topics, final ActorRef subscriber,
            final boolean acknowledge) {
        return new Unsubscribe(topics, subscriber, acknowledge);
    }
}
