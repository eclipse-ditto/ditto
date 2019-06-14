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
package org.eclipse.ditto.services.gateway.proxy.actors;

import org.eclipse.ditto.signals.commands.base.Command;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which delegates {@link Command}s to the appropriate receivers in the cluster.
 */
public final class ProxyActor extends AbstractThingProxyActor {

    @SuppressWarnings("unused")
    private ProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef conciergeForwarder) {

        super(pubSubMediator, devOpsCommandsActor, conciergeForwarder);
    }

    /**
     * Creates Akka configuration object Props for this ProxyActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param devOpsCommandsActor the Actor ref to the local DevOpsCommandsActor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef conciergeForwarder) {

        return Props.create(ProxyActor.class, pubSubMediator, devOpsCommandsActor, conciergeForwarder);
    }

}
