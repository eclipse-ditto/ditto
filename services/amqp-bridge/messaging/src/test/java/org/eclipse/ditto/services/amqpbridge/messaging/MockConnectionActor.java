/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.amqpbridge.messaging;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 * Mocks a {@link ConnectionActor} and provides abstraction for a real connection.
 */
public class MockConnectionActor extends AbstractActor {

    static final ConnectionActorPropsFactory mockConnectionActorPropsFactory =
            (amqpConnection, commandProcessor) -> MockConnectionActor.props();

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private MockConnectionActor() {
    }

    public static Props props() {
        return Props.create(MockConnectionActor.class, (Creator<MockConnectionActor>) MockConnectionActor::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CreateConnection.class, cc -> {
                    log.info("Creating connection...");
                    sender().tell("success", self());
                })
                .match(OpenConnection.class, oc -> {
                    log.info("Opening connection...");
                    sender().tell("success", self());
                })
                .match(CloseConnection.class, cc -> {
                    log.info("Closing connection...");
                    sender().tell("success", self());
                })
                .match(DeleteConnection.class, dc -> {
                    log.info("Deleting connection...");
                    sender().tell("success", self());
                })
                .build();
    }
}
