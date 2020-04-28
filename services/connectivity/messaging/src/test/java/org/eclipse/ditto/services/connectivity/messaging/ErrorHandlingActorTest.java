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

import static org.eclipse.ditto.services.connectivity.messaging.FaultyClientActor.faultyClientActorPropsFactory;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ConnectivityModifyCommand;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.javadsl.TestKit;

/**
 * Tests error handling behaviour of {@link org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceActor}.
 */
public class ErrorHandlingActorTest extends WithMockServers {

    private static ActorSystem actorSystem;
    private static ActorRef conciergeForwarder;
    private static ActorRef pubSubMediator;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        conciergeForwarder = actorSystem.actorOf(TestConstants.ConciergeForwarderActorMock.props());
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void tryCreateConnectionExpectSuccessResponseIndependentOfConnectionStatus() {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Connection connection = TestConstants.createConnection(connectionId);
            final ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem,
                    pubSubMediator, conciergeForwarder,
                    (connection1, connectionActor, conciergeForwarder) ->
                            FaultyClientActor.props(false));
            watch(underTest);

            // create connection
            final ConnectivityModifyCommand command = CreateConnection.of(connection, DittoHeaders.empty());
            underTest.tell(command, getRef());
            expectMsg(CreateConnectionResponse.of(connection, DittoHeaders.empty()));
        }};
    }

    @Test
    public void tryOpenConnectionExpectErrorResponse() {
        tryModifyConnectionExpectErrorResponse("open");
    }

    @Test
    public void tryCloseConnectionExpectErrorResponse() {
        tryModifyConnectionExpectErrorResponse("close");
    }

    @Test
    public void tryDeleteConnectionExpectErrorResponse() {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Connection connection = TestConstants.createConnection(connectionId);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder, faultyClientActorPropsFactory);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(connection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(connection, DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // delete connection
            final ConnectivityModifyCommand command = DeleteConnection.of(connectionId, DittoHeaders.empty());
            underTest.tell(command, getRef());
            expectMsg(DeleteConnectionResponse.of(connectionId, DittoHeaders.empty()));
        }};
    }

    private void tryModifyConnectionExpectErrorResponse(final String action) {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Connection connection = TestConstants.createConnection(connectionId);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder, faultyClientActorPropsFactory);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(connection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(connection, DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // modify connection
            final ConnectivityModifyCommand command;
            switch (action) {
                case "open":
                    command = OpenConnection.of(connectionId, DittoHeaders.empty());
                    break;
                case "close":
                    command = CloseConnection.of(connectionId, DittoHeaders.empty());
                    break;
                default:
                    throw new IllegalArgumentException("invalid action " + action);
            }
            underTest.tell(command, getRef());
            expectMsg(ConnectionFailedException
                    .newBuilder(connectionId)
                    .description("error message")
                    .build());
        }};
    }
}
