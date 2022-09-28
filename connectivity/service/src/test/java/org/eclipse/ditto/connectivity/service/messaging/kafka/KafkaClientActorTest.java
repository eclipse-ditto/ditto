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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link KafkaClientActor}.
 */
@SuppressWarnings({"squid:S3599", "squid:S1171"})
@RunWith(MockitoJUnitRunner.class)
public final class KafkaClientActorTest extends AbstractBaseClientActorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaClientActorTest.class);

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final String HOST = "127.0.0.1";
    private static final String TOPIC = "target";
    private static final Target TARGET = ConnectivityModelFactory.newTargetBuilder()
            .address(TOPIC)
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .qos(0)
            .topics(Topic.TWIN_EVENTS)
            .build();

    private static ActorSystem actorSystem;
    private static ServerSocket mockServer;

    private ConnectionId connectionId;
    private Connection connection;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        startMockServer();
    }

    private static void startMockServer() {
        mockServer = TestConstants.newMockServer();
        LOGGER.info("Started mock server on port {}", mockServer.getLocalPort());
    }

    @AfterClass
    public static void tearDown() {
        actorSystem.terminate();
        stopMockServer();
    }

    private static void stopMockServer() {
        if (null != mockServer) {
            try {
                mockServer.close();
                LOGGER.info("Successfully closed mock server.");
            } catch (final IOException e) {
                LOGGER.info("Got unexpected exception while closing the mock server.", e);
            }
        } else {
            LOGGER.info("Could not stop mock server as it unexpectedly was <null>.");
        }
    }

    @Before
    public void initializeConnection() {
        connectionId = TestConstants.createRandomConnectionId();
        final String hostAndPort = HOST + ":" + mockServer.getLocalPort();
        final String serverHost = "tcp://" + hostAndPort;
        final Map<String, String> specificConfig = specificConfigWithBootstrapServers(hostAndPort);
        connection = ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.KAFKA,
                ConnectivityStatus.CLOSED, serverHost)
                .targets(singletonList(TARGET))
                .failoverEnabled(true)
                .specificConfig(specificConfig)
                .build();
    }

    @Test
    public void testConnect() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(getSystem());
            final Props props = getKafkaClientActorProps(probe.ref(), connection);
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(10), CONNECTED_SUCCESS);

            kafkaClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            expectPublisherReceivedShutdownSignal(probe);
        }};
    }

    @Test
    public void testPublishToTopic() {
        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(getSystem());
            final Props props = getKafkaClientActorProps(probe.ref(), connection);
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(10), CONNECTED_SUCCESS);

            final ThingModifiedEvent<?> thingModifiedEvent =
                    TestConstants.thingModified(TARGET.getAuthorizationContext().getAuthorizationSubjects());
            final String expectedJson = TestConstants.signalToDittoProtocolJsonString(thingModifiedEvent);

            LOGGER.info("Sending thing modified message: {}", thingModifiedEvent);
            kafkaClientActor.tell(thingModifiedEvent, getRef());

            final OutboundSignal.MultiMapped message =
                    probe.expectMsgClass(OutboundSignal.MultiMapped.class);
            assertThat(message.first().getExternalMessage().getTextPayload()).contains(expectedJson);

            kafkaClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            expectPublisherReceivedShutdownSignal(probe);
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props = getKafkaClientActorProps(getRef(), connection);
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success("successfully connected + initialized mapper"));
        }};
    }

    @Test
    public void testTestConnectionFails() {
        new TestKit(actorSystem) {{
            final Props props = getKafkaClientActorProps(getRef(),
                    new Status.Failure(new IllegalStateException("just for testing")), connection);
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            fishForMessage(FiniteDuration.apply(10, TimeUnit.SECONDS), Status.Failure.class.getName(),
                    Status.Failure.class::isInstance);
        }};
    }

    @Test
    public void testRetrieveConnectionMetrics() {
        new TestKit(actorSystem) {{
            final Props props = getKafkaClientActorProps(getRef(), connection);
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(10), CONNECTED_SUCCESS);

            kafkaClientActor.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());

            expectMsgClass(RetrieveConnectionMetricsResponse.class);
        }};
    }

    private Props getKafkaClientActorProps(final ActorRef ref, final Connection connection) {
        return getKafkaClientActorProps(ref, new Status.Success(Done.done()), connection);
    }

    private Props getKafkaClientActorProps(final ActorRef ref, final Status.Status status,
            final Connection connection) {
        return KafkaClientActor.propsForTests(connection, ref, ref,
                new KafkaPublisherActorFactory() {
                    @Override
                    public String getActorName() {
                        return "testPublisherActor";
                    }

                    @Override
                    public Props props(final Connection c,
                            final SendProducerFactory producerFactory,
                            final boolean dryRun,
                            final ConnectivityStatusResolver connectivityStatusResolver,
                            final ConnectivityConfig connectivityConfig) {

                        return MockKafkaPublisherActor.props(ref, status);
                    }
                }, dittoHeaders);
    }

    private static Map<String, String> specificConfigWithBootstrapServers(final String... hostAndPort) {
        final Map<String, String> specificConfig = new HashMap<>();
        specificConfig.put("bootstrapServers", String.join(",", hostAndPort));
        return specificConfig;
    }

    @Override
    protected Connection getConnection(final boolean isSecure) {
        return isSecure ? setScheme(connection, "ssl") : connection;
    }

    @Override
    protected Props createClientActor(final ActorRef proxyActor, final Connection connection) {
        return getKafkaClientActorProps(proxyActor, connection);
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystem;
    }

    @Override
    @Test
    @Ignore("Kafka connections do not check certificate during connection test.")
    public void testTLSConnectionWithoutCertificateCheck() {
        super.testTLSConnectionWithoutCertificateCheck();
    }

    private static void expectPublisherReceivedShutdownSignal(final TestProbe probe) {
        probe.expectMsg(KafkaPublisherActor.GracefulStop.INSTANCE);
    }

    private static final class MockKafkaPublisherActor extends AbstractActor {

        private final ActorRef target;

        private MockKafkaPublisherActor(final ActorRef target, final Status.Status status) {
            this.target = target;
            getContext().getParent().tell(status, getSelf());
        }

        static Props props(final ActorRef target, final Status.Status status) {
            return Props.create(MockKafkaPublisherActor.class, target, status);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(any -> target.forward(any, getContext())).build();
        }

    }

}
