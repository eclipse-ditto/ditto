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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ConnectivityModifyCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.javadsl.TestKit;

/**
 * Tests error handling behaviour of {@link org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor}.
 */
public class ErrorHandlingActorTest extends WithMockServers {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static ActorSystem actorSystem;
    private static ActorRef proxyActor;
    private static ActorRef pubSubMediator;
    private static Duration CONNECT_TIMEOUT;
    private static Duration DISCONNECT_TIMEOUT;

    public void setUp(final boolean allowFirstCreateCommand, final boolean allowCloseCommands) {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.parseMap(
                        Map.of("ditto.extensions.client-actor-props-factory",
                                "org.eclipse.ditto.connectivity.service.messaging.FaultyClientActorPropsFactory",
                                "allowFirstCreateCommand", allowFirstCreateCommand, "allowCloseCommands",
                                allowCloseCommands))
                .withFallback(TestConstants.CONFIG));
        final DittoConnectivityConfig connectivityConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        CONNECT_TIMEOUT = connectivityConfig.getClientConfig().getConnectingMinTimeout();
        DISCONNECT_TIMEOUT = connectivityConfig.getClientConfig().getDisconnectAnnouncementTimeout()
                .plus(connectivityConfig.getClientConfig().getDisconnectingMaxTimeout());
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        proxyActor = actorSystem.actorOf(TestConstants.ProxyActorMock.props());
    }

    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void tryCreateConnectionExpectSuccessResponseIndependentOfConnectionStatus() {
        setUp(true, false);
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Connection connection = TestConstants.createConnection(connectionId);
            final ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem,
                    pubSubMediator, proxyActor);
            watch(underTest);

            // create connection
            final ConnectivityModifyCommand<?> command = CreateConnection.of(connection, DittoHeaders.empty());
            underTest.tell(command, getRef());
            final CreateConnectionResponse resp =
                    expectMsgClass(dilated(CONNECT_TIMEOUT), CreateConnectionResponse.class);
            Assertions.assertThat(resp.getConnection())
                    .usingRecursiveComparison()
                    .ignoringFields("revision", "modified", "created")
                    .isEqualTo(connection);
        }};
        tearDown();
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
        setUp(true, true);
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Connection connection = TestConstants.createConnection(connectionId);

            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(connection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse resp =
                    expectMsgClass(dilated(CONNECT_TIMEOUT), CreateConnectionResponse.class);
            Assertions.assertThat(resp.getConnection())
                    .usingRecursiveComparison()
                    .ignoringFields("revision", "modified", "created")
                    .isEqualTo(connection);

            // delete connection
            final ConnectivityModifyCommand<?> command = DeleteConnection.of(connectionId, DittoHeaders.empty());
            underTest.tell(command, getRef());
            expectMsg(dilated(DISCONNECT_TIMEOUT),
                    DeleteConnectionResponse.of(connectionId, DittoHeaders.empty()));
        }};
        tearDown();
    }

    private void tryModifyConnectionExpectErrorResponse(final String action) {
        setUp(true, false);
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Connection connection = TestConstants.createConnection(connectionId);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor);
            watch(underTest);

            // create connection
            final CreateConnection createConnection = CreateConnection.of(connection, DittoHeaders.empty());
            underTest.tell(createConnection, getRef());
            final CreateConnectionResponse resp =
                    expectMsgClass(dilated(CONNECT_TIMEOUT), CreateConnectionResponse.class);
            Assertions.assertThat(resp.getConnection())
                    .usingRecursiveComparison()
                    .ignoringFields("revision", "modified", "created")
                    .isEqualTo(connection);

            // modify connection
            final ConnectivityModifyCommand<?> command;
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
        tearDown();
    }
}
