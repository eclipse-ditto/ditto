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
package org.eclipse.ditto.services.utils.pubsub.ddata;

import java.util.Collection;
import java.util.Set;

import akka.actor.ActorRef;

/**
 * Reader of local subscriptions.
 */
public interface SubscriptionsReader {

    /**
     * Look up the set of subscribers subscribing to at least one of the given topics.
     *
     * @param topics the topics.
     * @return the set of subscribers.
     */
    Set<ActorRef> getSubscribers(Collection<String> topics);
}
