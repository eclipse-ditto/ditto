/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 * Mocks a {@link ConnectionActor} and provides abstraction for a real connection.
 */
public class MockClientActor extends AbstractActor {

    static final ClientActorPropsFactory mockClientActorPropsFactory =
            (connection, conciergeForwarder) -> MockClientActor.props();

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef delegate;

    private MockClientActor(final ActorRef delegate) {
        this.delegate = delegate;
    }

    public static Props props() {
        return Props.create(MockClientActor.class, (Creator<MockClientActor>) () -> new MockClientActor(null));
    }

    public static Props props(final ActorRef delegate) {
        return Props.create(MockClientActor.class, () -> new MockClientActor(delegate));
    }

    @Override
    public void preStart() {
        log.info("Mock client actor started.");
    }

    @Override
    public void postStop() {
        log.info("Mock client actor was stopped.");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CreateConnection.class, cc -> {
                    log.info("Creating connection...");
                    forward(cc);
                    sender().tell(new Status.Success("mock"), getSelf());
                })
                .match(OpenConnection.class, oc -> {
                    log.info("Opening connection...");
                    forward(oc);
                    sender().tell(new Status.Success("mock"), getSelf());
                })
                .match(ModifyConnection.class, mc -> {
                    log.info("Modifying connection...");
                    forward(mc);
                    sender().tell(new Status.Success("mock"), getSelf());
                })
                .match(CloseConnection.class, cc -> {
                    log.info("Closing connection...");
                    forward(cc);
                    sender().tell(new Status.Success("mock"), getSelf());
                })
                .match(DeleteConnection.class, dc -> {
                    log.info("Deleting connection...");
                    forward(dc);
                    sender().tell(new Status.Success("mock"), getSelf());
                })
                .matchAny(unhandled -> {
                    log.info("Received unhandled message: {}", unhandled.getClass().getName());
                    forward(unhandled);
                })
                .build();
    }

    private void forward(final Object obj) {
        if (delegate != null) {
            delegate.tell(obj, getSelf());
        }
    }
}
