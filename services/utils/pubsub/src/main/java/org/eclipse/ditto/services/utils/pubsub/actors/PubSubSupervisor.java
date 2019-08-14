/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.actors;

import akka.actor.AbstractActor;

/**
 * Supervisor of all actors handling distributed pub-sub.
 * <pre>
 * {@code
 *                                PubSubSupervisor
 *                                      +
 *                                      |
 *             +------------------------+super-vises one-for-many
 *             |                        +---------------------------+
 *             |                        |                           |
 *             v                        v                           v
 *    PubSubPublisher ----------> PubSubUpdater +-----------> PubSubSubscriber
 *         +          dead letters   +           update
 *         |          in case remote |           local
 *         |          member dies    |           subscriptions
 *         |                         |
 *         |                         |
 *         |                         |
 *         |read local               |write with highest requested consistency
 *         |                         v
 *         +--------------------> DDataReplicator
 * }
 * </pre>
 */
public final class PubSubSupervisor extends AbstractActor {

    // TODO: if one child fails, restart them all.

    @Override
    public Receive createReceive() {
        // TODO
        return null;
    }

}
