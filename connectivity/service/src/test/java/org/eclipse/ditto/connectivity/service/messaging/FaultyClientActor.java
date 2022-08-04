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
package org.eclipse.ditto.connectivity.service.messaging;

import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;

/**
 * A ClientActor implementation that fails for every command received and answers with an exception. If {@code
 * allowCreate} is {@code true} the first create command will return success (required to test open/close/delete).
 */
public class FaultyClientActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final boolean allowClose;
    private boolean allowCreate;

    private FaultyClientActor(final boolean allowCreate, final boolean allowClose) {
        this.allowCreate = allowCreate;
        this.allowClose = allowClose;
    }

    public static Props props(final boolean allowFirstCreateCommand, final boolean allowCloseCommands) {
        return Props.create(FaultyClientActor.class, allowFirstCreateCommand, allowCloseCommands);
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
                        cc -> {
                            if (allowClose) {
                                sender().tell(new Status.Success("mock"), getSelf());
                            } else {
                                sender().tell(new Status.Failure(new IllegalStateException("error message")),
                                        getSelf());
                            }
                        })
                .match(DeleteConnection.class,
                        dc -> sender().tell(new Status.Failure(new IllegalStateException("error message")),
                                getSelf()))
                .build();
    }

}
