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

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.javadsl.TestKit;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;
import org.eclipse.ditto.signals.commands.amqpbridge.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatusResponse;

/**
 * Unit test for {@link ConnectionActor}.
 */
public class ConnectionActorTest {

    private static final Config CONFIG = ConfigFactory.load("test");
    private static final String PROXY_ACTOR_PATH = "/user/gatewayRoot/proxy";

    private static final String URI = "amqps://username:password@my.endpoint:443";
    private static final String SUBJECT_ID = "mySolutionId:mySubject";
    private static final AuthorizationSubject AUTHORIZATION_SUBJECT = AuthorizationSubject.newInstance(SUBJECT_ID);
    private static final Set<String> SOURCES = new HashSet<>(Arrays.asList("amqp/source1", "amqp/source2"));
    private static final boolean FAILOVER = false;
    private static final JmsConnectionFactory CONNECTION_FACTORY = new MockJmsConnectionFactory();

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
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
            final String connectionId = createRandomConnectionId();
            final ActorRef underTest = createAmqpConnectionActor(connectionId);

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
            final String connectionId = createRandomConnectionId();
            final AmqpConnection amqpConnection = createConnection(connectionId);
            final ActorRef underTest = createAmqpConnectionActor(connectionId);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, DittoHeaders.empty());
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
            final String connectionId = createRandomConnectionId();
            final AmqpConnection amqpConnection = createConnection(connectionId);
            ActorRef underTest = createAmqpConnectionActor(connectionId);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // stop actor
            getSystem().stop(underTest);
            expectTerminated(underTest);

            // recover actor
            underTest = createAmqpConnectionActor(connectionId);

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
            final String connectionId = createRandomConnectionId();
            final AmqpConnection amqpConnection = createConnection(connectionId);
            ActorRef underTest = createAmqpConnectionActor(connectionId);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, DittoHeaders.empty());
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
            underTest = createAmqpConnectionActor(connectionId);

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
            final String connectionId = createRandomConnectionId();
            final AmqpConnection amqpConnection = createConnection(connectionId);
            ActorRef underTest = createAmqpConnectionActor(connectionId);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // delete connection
            final DeleteConnection deleteConnection = DeleteConnection.of(connectionId, DittoHeaders.empty());
            underTest.tell(deleteConnection, getRef());
            final DeleteConnectionResponse deleteConnectionResponse =
                    DeleteConnectionResponse.of(connectionId, DittoHeaders.empty());
            expectMsg(deleteConnectionResponse);
            expectTerminated(underTest);

            // recover actor
            underTest = createAmqpConnectionActor(connectionId);

            // retrieve connection status
            final RetrieveConnectionStatus retrieveConnectionStatus =
                    RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty());
            underTest.tell(retrieveConnectionStatus, getRef());
            final ConnectionNotAccessibleException connectionNotAccessibleException =
                    ConnectionNotAccessibleException.newBuilder(connectionId).build();
            expectMsg(connectionNotAccessibleException);
        }};
    }

    private static ActorRef createAmqpConnectionActor(final String connectionId) {
        final Duration minBackoff = Duration.ofSeconds(1);
        final Duration maxBackoff = Duration.ofSeconds(5);
        final Double randomFactor = 1.0;
        final Props props = ConnectionSupervisorActor.props(minBackoff, maxBackoff, randomFactor,
                pubSubMediator,
                PROXY_ACTOR_PATH,
                CONNECTION_FACTORY);
        return actorSystem.actorOf(props, connectionId);
    }

    private static String createRandomConnectionId() {
        return "connection-" + UUID.randomUUID();
    }

    private static AmqpConnection createConnection(final String connectionId) {
        return AmqpBridgeModelFactory.newConnection(connectionId, URI, AUTHORIZATION_SUBJECT, SOURCES, FAILOVER);
    }

}
