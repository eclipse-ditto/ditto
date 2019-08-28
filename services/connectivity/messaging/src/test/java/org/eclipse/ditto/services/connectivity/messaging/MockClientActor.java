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
package org.eclipse.ditto.services.connectivity.messaging;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.modify.CheckConnectionLogsActive;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;

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
        return Props.create(MockClientActor.class, delegate);
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
                .match(RetrieveConnectionStatus.class, rcs -> {
                    log.info("Retrieve connection status...");
                    sender().tell(ConnectivityModelFactory.newClientStatus("client1",
                            ConnectivityStatus.OPEN, "connection is open", TestConstants.INSTANT),
                            getSelf());

                    // simulate consumer and pusblisher actor response
                    sender().tell(ConnectivityModelFactory.newSourceStatus("client1",
                            ConnectivityStatus.OPEN, "source1","consumer started"),
                            getSelf());
                    sender().tell(ConnectivityModelFactory.newSourceStatus("client1",
                            ConnectivityStatus.OPEN, "source2","consumer started"),
                            getSelf());
                    sender().tell(ConnectivityModelFactory.newTargetStatus("client1",
                            ConnectivityStatus.OPEN, "target1","publisher started"),
                            getSelf());
                })
                .match(RetrieveConnectionLogs.class, rcl -> {
                    log.info("Retrieve connection logs...");
                    // forwarding to delegate so it can respond to correct sender
                    if (null != delegate) {
                        delegate.forward(rcl, getContext());
                    } else {
                        log.error("No delegate found in MockClientActor. RetrieveConnectionLogs needs a delegate which" +
                                " needs to respond with a RetrieveConnectionLogsResponse to the sender of the command");
                    }
                })
                .match(EnableConnectionLogs.class, ecl-> {
                    log.info("Enable connection logs...");
                    forward(ecl);
                })
                .match(TestConnection.class, testConnection -> {
                    log.info("Testing connection");
                    final DittoRuntimeException exception =
                            DittoRuntimeException.newBuilder("some.error", HttpStatusCode.BAD_REQUEST).build();

                    if (testConnection.getDittoHeaders().getOrDefault("fail", "").equals("true")) {
                        sender().tell(new Status.Failure(exception), getSelf());
                    }

                    sender().tell(new Status.Success("mock"), getSelf());
                })
                .match(CheckConnectionLogsActive.class, ccla -> {
                    log.info("Check connection logs active...");
                    forward(ccla);
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
