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

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

public class FaultyConnectionActor extends AbstractActor {

    static final ConnectionActorPropsFactory faultyConnectionActorPropsFactory =
            (amqpConnection, commandProcessor) -> FaultyConnectionActor.props(amqpConnection, commandProcessor, true);

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final AmqpConnection amqpConnection;
    private final ActorRef commandProcessor;
    private final boolean allowCreate;

    private FaultyConnectionActor(final AmqpConnection amqpConnection, final ActorRef commandProcessor,
            final boolean allowCreate) {
        this.amqpConnection = amqpConnection;
        this.commandProcessor = commandProcessor;
        this.allowCreate = allowCreate;
    }

    public static Props props(final AmqpConnection amqpConnection, final ActorRef commandProcessor,
            final boolean allowCreate) {
        return Props.create(FaultyConnectionActor.class, new Creator<FaultyConnectionActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public FaultyConnectionActor create() {
                return new FaultyConnectionActor(amqpConnection, commandProcessor, allowCreate);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CreateConnection.class, cc -> {
                    if (allowCreate) {
                        log.info("connection created");
                        sender().tell("success", self());
                    } else {
                        sender().tell(new Status.Failure(new IllegalStateException("cannot create connection")),
                                self());
                    }
                })
                .match(OpenConnection.class,
                        oc -> sender().tell(new Status.Failure(new IllegalStateException("cannot open connection")),
                                self()))
                .match(CloseConnection.class,
                        cc -> sender().tell(new Status.Failure(new IllegalStateException("cannot close connection")),
                                self()))
                .match(DeleteConnection.class,
                        dc -> sender().tell(new Status.Failure(new IllegalStateException("cannot delete connection")),
                                self()))
                .build();
    }
}
