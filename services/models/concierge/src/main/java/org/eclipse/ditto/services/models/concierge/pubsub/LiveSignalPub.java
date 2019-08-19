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

import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.ActorSystem;

/**
 * Publishing of all live signals.
 * <p>
 * CAUTION: Only one instance of LiveSignalPub or LiveSignalSub can be started in the same actor system.
 * In particular, it is an error to start both LiveSignalPub and LiveSignalSub.
 */
public interface LiveSignalPub {

    /**
     * Start a live signal pub in an actor system.
     *
     * @param actorSystem the actor system.
     * @return the live signal pub.
     */
    static LiveSignalPub of(final ActorSystem actorSystem) {
        return LiveSignalPubImpl.of(actorSystem);
    }

    /**
     * @return Distributed-pub access for live commands.
     */
    DistributedPub<Command> command();

    /**
     * @return Distributed-pub access for live events.
     */
    DistributedPub<Event> event();

    /**
     * @return Distributed-pub access for messages.
     */
    DistributedPub<Signal> message();
}
