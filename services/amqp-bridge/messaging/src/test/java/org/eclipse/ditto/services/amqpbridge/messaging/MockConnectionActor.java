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

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 * Mocks a {@link ConnectionActor} and provides abstraction for a real connection.
 */
public class MockConnectionActor extends ConnectionActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private MockConnectionActor(final String connectionId, final ActorRef pubSubMediator,
            final String pubSubTargetActorPath) {
        super(connectionId, pubSubMediator, pubSubTargetActorPath);
    }

    public static Props props(final String connectionId, final ActorRef pubSubMediator,
            final String pubSubTargetActorPath) {
        return Props.create(MockConnectionActor.class, new Creator<MockConnectionActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MockConnectionActor create() {
                return new MockConnectionActor(connectionId, pubSubMediator, pubSubTargetActorPath);
            }
        });
    }

    @Override
    protected void doCreateConnection(final CreateConnection createConnection) {
        log.info("creating....");
    }

    @Override
    protected void doOpenConnection(final OpenConnection openConnection) {
        log.info("opening....");
    }

    @Override
    protected void doCloseConnection(final CloseConnection closeConnection) {
        log.info("closing....");
    }

    @Override
    protected void doDeleteConnection(final DeleteConnection deleteConnection) {
        log.info("deleting....");
    }

    @Override
    protected void doUpdateConnection(final AmqpConnection amqpConnection) {
        log.info("updating....");
    }
}
