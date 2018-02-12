/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.messaging;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.amqpbridge.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatusResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link ConnectionActor}.
 */
public class ConnectionActorTest {

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void tryToSendOtherCommandThanCreateDuringInitialization() {
        new TestKit(actorSystem) {{
            final String connectionId = TestConstants.createRandomConnectionId();
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator);

            final DeleteConnection deleteConnection = DeleteConnection.of(connectionId, DittoHeaders.empty());
            underTest.tell(deleteConnection, getRef());

            final ConnectionNotAccessibleException expectedMessage =
                    ConnectionNotAccessibleException.newBuilder(connectionId).build();
            expectMsg(expectedMessage);
        }};
    }

    @Test
    public void manageConnection() {
        new TestKit(actorSystem) {{
            final String connectionId = TestConstants.createRandomConnectionId();
            final AmqpConnection amqpConnection = TestConstants.createConnection(connectionId);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, Collections.emptyList(), DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // close connection
            final CloseConnection closeConnection = CloseConnection.of(connectionId, DittoHeaders.empty());
            underTest.tell(closeConnection, getRef());
            final CloseConnectionResponse closeConnectionResponse =
                    CloseConnectionResponse.of(connectionId, DittoHeaders.empty());
            expectMsg(closeConnectionResponse);

            // delete connection
            final DeleteConnection deleteConnection = DeleteConnection.of(connectionId, DittoHeaders.empty());
            underTest.tell(deleteConnection, getRef());
            final DeleteConnectionResponse deleteConnectionResponse =
                    DeleteConnectionResponse.of(connectionId, DittoHeaders.empty());
            expectMsg(deleteConnectionResponse);
            expectTerminated(underTest);
        }};
    }

    @Test
    public void recoverOpenConnection() {
        new TestKit(actorSystem) {{
            final String connectionId = TestConstants.createRandomConnectionId();
            final AmqpConnection amqpConnection = TestConstants.createConnection(connectionId);
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, Collections.emptyList(), DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // stop actor
            getSystem().stop(underTest);
            expectTerminated(underTest);

            // recover actor
            underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator);

            // retrieve connection status
            final RetrieveConnectionStatus retrieveConnectionStatus =
                    RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty());
            underTest.tell(retrieveConnectionStatus, getRef());
            final RetrieveConnectionStatusResponse retrieveConnectionStatusResponse =
                    RetrieveConnectionStatusResponse.of(connectionId, ConnectionStatus.OPEN, DittoHeaders.empty());
            expectMsg(retrieveConnectionStatusResponse);
        }};
    }

    @Test
    public void recoverClosedConnection() {
        new TestKit(actorSystem) {{
            final String connectionId = TestConstants.createRandomConnectionId();
            final AmqpConnection amqpConnection = TestConstants.createConnection(connectionId);
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, Collections.emptyList(), DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // close connection
            final CloseConnection closeConnection = CloseConnection.of(connectionId, DittoHeaders.empty());
            underTest.tell(closeConnection, getRef());
            final CloseConnectionResponse closeConnectionResponse =
                    CloseConnectionResponse.of(connectionId, DittoHeaders.empty());
            expectMsg(closeConnectionResponse);

            // stop actor
            getSystem().stop(underTest);
            expectTerminated(underTest);

            // recover actor
            underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator);

            // retrieve connection status
            final RetrieveConnectionStatus retrieveConnectionStatus =
                    RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty());
            underTest.tell(retrieveConnectionStatus, getRef());
            final RetrieveConnectionStatusResponse retrieveConnectionStatusResponse =
                    RetrieveConnectionStatusResponse.of(connectionId, ConnectionStatus.CLOSED, DittoHeaders.empty());
            expectMsg(retrieveConnectionStatusResponse);
        }};
    }

    @Test
    public void recoverDeletedConnection() {
        new TestKit(actorSystem) {{
            final String connectionId = TestConstants.createRandomConnectionId();
            final AmqpConnection amqpConnection = TestConstants.createConnection(connectionId);
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, Collections.emptyList(), DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // delete connection
            final DeleteConnection deleteConnection = DeleteConnection.of(connectionId, DittoHeaders.empty());
            underTest.tell(deleteConnection, getRef());
            final DeleteConnectionResponse deleteConnectionResponse =
                    DeleteConnectionResponse.of(connectionId, DittoHeaders.empty());
            expectMsg(deleteConnectionResponse);
            expectTerminated(underTest);

            // recover actor
            underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator);

            // retrieve connection status
            final RetrieveConnectionStatus retrieveConnectionStatus =
                    RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty());
            underTest.tell(retrieveConnectionStatus, getRef());
            final ConnectionNotAccessibleException connectionNotAccessibleException =
                    ConnectionNotAccessibleException.newBuilder(connectionId).build();
            expectMsg(connectionNotAccessibleException);
        }};
    }

}
