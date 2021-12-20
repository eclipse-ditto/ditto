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

import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorContext;

/**
 * Publishing of all live signals.
 */
public interface LiveSignalPub {

    /**
     * Start a live signal pub in an actor system.
     *
     * @param context context of the actor under which pub and sub actors are started.
     * @return the live signal pub.
     */
    static LiveSignalPub of(final ActorContext context, final DistributedAcks distributedAcks) {
        return LiveSignalPubImpl.of(context, distributedAcks);
    }

    /**
     * @return Distributed-pub access for live commands.
     */
    DistributedPub<ThingCommand<?>> command();

    /**
     * @return Distributed-pub access for live events.
     */
    DistributedPub<ThingEvent<?>> event();

    /**
     * @return Distributed-pub access for messages.
     */
    DistributedPub<SignalWithEntityId<?>> message();

}
