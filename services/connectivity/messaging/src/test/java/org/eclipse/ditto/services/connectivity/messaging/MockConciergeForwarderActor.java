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
package org.eclipse.ditto.services.connectivity.messaging;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * Mock actor that forwards all messages to the final ActorRef message.
 */
final class MockConciergeForwarderActor extends AbstractActor {

    /**
     * Create a mock /connectivityRoot/conciergeForwarder actor that forwards everything to the last ActorRef message it
     * received.
     *
     * @param actorSystem the actor system where the mock concierge forwarder is to be created.
     */
    public static void create(final ActorSystem actorSystem) {
        actorSystem.actorOf(Props.create(MockConciergeForwarderActor.class), "connectivityRoot");
    }

    @Override
    public void preStart() {
        getContext().actorOf(Props.create(MockInnerActor.class), "conciergeForwarder");
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create().build();
    }

    private static final class MockInnerActor extends AbstractActor {

        private ActorRef recipient;

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(ActorRef.class, actorRef -> {
                        recipient = actorRef;
                        getSender().tell(actorRef, getSelf());
                    })
                    .matchAny(message -> {
                        if (recipient != null) {
                            recipient.forward(message, getContext());
                        }
                    })
                    .build();
        }
    }
}
