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
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 * A ClientActor implementation that fails for every command received and answers with an exception.
 * If {@code allowCreate} is {@code true} the first create command will return success (required to test open/close/delete).
 */
public class FaultyClientActor extends AbstractActor {

    static final ClientActorPropsFactory faultyClientActorPropsFactory =
            (connection, conciergeForwarder) -> FaultyClientActor.props(true);

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private boolean allowCreate;

    private FaultyClientActor(final boolean allowCreate) {
        this.allowCreate = allowCreate;
    }

    public static Props props(final boolean allowFirstCreateCommand) {
        return Props.create(FaultyClientActor.class, new Creator<FaultyClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public FaultyClientActor create() {
                return new FaultyClientActor(allowFirstCreateCommand);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CreateConnection.class, cc -> {
                    if (allowCreate) {
                        log.info("connection created");
                        this.allowCreate = false;
                        sender().tell(new Status.Success("mock"), getSelf());
                    } else {
                        sender().tell(new Status.Failure(new IllegalStateException("error message")),
                                getSelf());
                    }
                })
                .match(OpenConnection.class,
                        oc -> sender().tell(new Status.Failure(new IllegalStateException("error message")),
                                getSelf()))
                .match(CloseConnection.class,
                        cc -> sender().tell(new Status.Failure(new IllegalStateException("error message")),
                                getSelf()))
                .match(DeleteConnection.class,
                        dc -> sender().tell(new Status.Failure(new IllegalStateException("error message")),
                                getSelf()))
                .build();
    }
}
