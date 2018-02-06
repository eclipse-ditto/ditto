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

public class FaultyConnectionActor extends ConnectionActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final boolean allowCreate;

    private FaultyConnectionActor(final String connectionId, final ActorRef pubSubMediator,
            final String pubSubTargetActorPath, final boolean allowCreate) {
        super(connectionId, pubSubMediator, pubSubTargetActorPath);
        this.allowCreate = allowCreate;
    }

    public static Props props(final String connectionId, final ActorRef pubSubMediator,
            final String pubSubTargetActorPath, final boolean allowCreate) {
        return Props.create(FaultyConnectionActor.class, new Creator<FaultyConnectionActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public FaultyConnectionActor create() {
                return new FaultyConnectionActor(connectionId, pubSubMediator, pubSubTargetActorPath, allowCreate);
            }
        });
    }

    @Override
    protected void doCreateConnection(final CreateConnection createConnection) {
        if (allowCreate) {
            log.info("connection created");
        } else {
            throw new IllegalStateException("cannot create connection");
        }
    }

    @Override
    protected void doOpenConnection(final OpenConnection openConnection) {
        throw new IllegalStateException("cannot open connection");
    }

    @Override
    protected void doCloseConnection(final CloseConnection closeConnection) {
        throw new IllegalStateException("cannot close connection");
    }

    @Override
    protected void doDeleteConnection(final DeleteConnection deleteConnection) {
        throw new IllegalStateException("cannot delete connection");
    }

    @Override
    protected void doUpdateConnection(final AmqpConnection amqpConnection) {
        throw new IllegalStateException("cannot update connection");
    }
}
