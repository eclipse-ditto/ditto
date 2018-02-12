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

import static org.eclipse.ditto.services.amqpbridge.messaging.FaultyConnectionActor.faultyConnectionActorPropsFactory;
import static org.eclipse.ditto.services.amqpbridge.messaging.TestConstants.createConnection;
import static org.eclipse.ditto.services.amqpbridge.messaging.TestConstants.createRandomConnectionId;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.amqpbridge.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.AmqpBridgeModifyCommand;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.javadsl.TestKit;

/**
 * Tests error handling behaviour of {@link ConnectionActor}.
 */
public class ErrorHandlingActorTest {

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
    public void tryCreateConnectionExpectErrorResponse() {
        new TestKit(actorSystem) {{
            final String connectionId = createRandomConnectionId();
            final AmqpConnection amqpConnection = createConnection(connectionId);
            final ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem,
                    pubSubMediator, (a, c) -> FaultyConnectionActor.props(a, c, false));
            watch(underTest);

            // create connection
            final AmqpBridgeModifyCommand command = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(command, getRef());
            expectMsg(ConnectionFailedException
                    .newBuilder(connectionId)
                    .description("cannot create connection")
                    .build());
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
        tryModifyConnectionExpectErrorResponse("delete");
    }

    private void tryModifyConnectionExpectErrorResponse(final String action) {
        new TestKit(actorSystem) {{
            final String connectionId = createRandomConnectionId();
            final AmqpConnection amqpConnection = createConnection(connectionId);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            faultyConnectionActorPropsFactory);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse createConnectionResponse =
                    CreateConnectionResponse.of(amqpConnection, new ArrayList<>(), DittoHeaders.empty());
            expectMsg(createConnectionResponse);

            // modify connection
            final AmqpBridgeModifyCommand command;
            switch (action) {
                case "open":
                    command = OpenConnection.of(connectionId, DittoHeaders.empty());
                    break;
                case "close":
                    command = CloseConnection.of(connectionId, DittoHeaders.empty());
                    break;
                case "delete":
                    command = DeleteConnection.of(connectionId, DittoHeaders.empty());
                    break;
                default:
                    throw new IllegalArgumentException("invalid action " + action);
            }
            underTest.tell(command, getRef());
            expectMsg(ConnectionFailedException
                    .newBuilder(connectionId)
                    .description("cannot " + action + " connection")
                    .build());
        }};
    }
}
