/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.mqtt.ReceiveMaximum;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.ChildActorNanny;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.AllSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttClientConnectException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.NoMqttConnectionException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SubscriptionStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming.MqttConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.MqttPublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscriber;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscribeResult;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import akka.actor.Props;
import akka.actor.Status;
import akka.pattern.AskTimeoutException;
import akka.testkit.TestActorRef;

/**
 * Unit test for {@link ConnectionTesterActor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ConnectionTesterActorTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();
    private static final long CLIENT_DISCONNECT_TIMEOUT = 500L;

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance();

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock private GenericMqttClientFactory genericMqttClientFactory;
    @Mock private GenericMqttClient genericMqttClient;
    @Mock private Connection connection;
    @Mock private ConnectivityConfig connectivityConfig;
    @Mock private ConnectionLogger connectionLogger;
    @Mock private ConnectivityStatusResolver connectivityStatusResolver;
    private Props connectionTesterActorProps;

    @Before
    public void before() throws NoMqttConnectionException {
        Mockito.when(connection.getConnectionType()).thenReturn(ConnectionType.MQTT);
        Mockito.when(connection.getId()).thenReturn(CONNECTION_ID);
        Mockito.when(connection.getUri()).thenReturn("example.com");

        final var connectionConfig = Mockito.mock(ConnectionConfig.class);
        final var mqttConfig = Mockito.mock(MqttConfig.class);
        Mockito.when(mqttConfig.getClientReceiveMaximum()).thenReturn(ReceiveMaximum.defaultReceiveMaximum());
        Mockito.when(connectionConfig.getMqttConfig()).thenReturn(mqttConfig);
        Mockito.when(connectivityConfig.getConnectionConfig()).thenReturn(connectionConfig);

        Mockito.when(genericMqttClient.connect(Mockito.any(GenericMqttConnect.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Mockito.when(genericMqttClientFactory.getGenericMqttClient(Mockito.any())).thenReturn(genericMqttClient);

        connectionTesterActorProps = ConnectionTesterActor.props(connectivityConfig,
                SshTunnelState::disabled,
                connectionLogger,
                UUID.randomUUID(),
                connectivityStatusResolver,
                genericMqttClientFactory);
    }

    @Test
    public void testConnectionReturnsFailureStatusWhenConnectionTypeIsNotMqtt() {
        final var kafkaConnection = Mockito.mock(Connection.class);
        Mockito.when(kafkaConnection.getConnectionType()).thenReturn(ConnectionType.KAFKA);
        final var underTest = actorSystemResource.newActor(connectionTesterActorProps);
        final var testKit = actorSystemResource.newTestKit();
        underTest.tell(TestConnection.of(kafkaConnection, getDittoHeadersWithCorrelationId()), testKit.getRef());

        final var failure = testKit.expectMsgClass(Status.Failure.class);
        assertThat(failure.cause()).isInstanceOf(NoMqttConnectionException.class);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.anyString());
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void testConnectionReturnsFailureStatusWhenGettingGenericMqttClientFails() {
        final var error = new IllegalArgumentException("Some argument is invalid.");
        Mockito.when(genericMqttClientFactory.getGenericMqttClient(Mockito.any())).thenThrow(error);
        final var underTest = actorSystemResource.newActor(connectionTesterActorProps);
        final var testKit = actorSystemResource.newTestKit();

        underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

        final var failure = testKit.expectMsgClass(Status.Failure.class);
        assertThat(failure.cause()).isEqualTo(error);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.eq(error.getMessage()));
    }

    @Test
    public void testConnectionWhenConnectingGenericMqttClientFailsReturnsFailureStatus() {
        final var error = new MqttClientConnectException("The broker is temporarily not available", null);
        Mockito.when(genericMqttClient.connect(Mockito.any(GenericMqttConnect.class)))
                .thenReturn(CompletableFuture.failedFuture(error));
        final var underTest = actorSystemResource.newActor(connectionTesterActorProps);
        final var testKit = actorSystemResource.newTestKit();

        underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

        final var failure = testKit.expectMsgClass(Status.Failure.class);
        assertThat(failure.cause()).isEqualTo(error);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.eq(error.getMessage()));
        Mockito.verify(genericMqttClient, Mockito.timeout(CLIENT_DISCONNECT_TIMEOUT)).disconnect();
    }

    @Test
    public void testConnectionWorksAsExpectedIfSuccessful() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        final var childActorNanny = Mockito.mock(ChildActorNanny.class);
        final var mqttSubscriber = Mockito.mock(MqttSubscriber.class);
        Mockito.when(mqttSubscriber.subscribeForConnectionSources(Mockito.any()))
                .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                        getSourceSubscribeSuccess(connectionSource1),
                        getSourceSubscribeSuccess(connectionSource2)
                )));
        try (
                final var childActorNannyMock = Mockito.mockStatic(ChildActorNanny.class);
                final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)
        ) {
            childActorNannyMock.when(() -> ChildActorNanny.newInstance(Mockito.any(), Mockito.any()))
                    .thenReturn(childActorNanny);
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

            final var publisherActorTestKit = actorSystemResource.newTestKit();
            Mockito.when(childActorNanny.startChildActorConflictFree(Mockito.eq(MqttPublisherActor.class.getSimpleName()),
                            Mockito.any(Props.class)))
                    .thenReturn(publisherActorTestKit.getRef());
            final var consumerActor1TestKit = actorSystemResource.newTestKit();
            final var consumerActor2TestKit = actorSystemResource.newTestKit();
            Mockito.when(childActorNanny.startChildActorConflictFree(Mockito.eq(MqttConsumerActor.class.getSimpleName()),
                            Mockito.any(Props.class)))
                    .thenReturn(consumerActor1TestKit.getRef())
                    .thenReturn(consumerActor2TestKit.getRef());

            final var underTest = TestActorRef.apply(connectionTesterActorProps, actorSystemResource.getActorSystem());
            final var testKit = actorSystemResource.newTestKit();

            underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

            final var retrieveHealth = RetrieveHealth.newInstance();
            final var retrieveHealthResponse = RetrieveHealthResponse.of(StatusInfo.fromStatus(StatusInfo.Status.UP),
                    retrieveHealth.getDittoHeaders());
            publisherActorTestKit.expectMsg(retrieveHealth);
            publisherActorTestKit.reply(retrieveHealthResponse);
            consumerActor1TestKit.expectMsg(retrieveHealth);
            consumerActor1TestKit.reply(retrieveHealthResponse);
            consumerActor2TestKit.expectMsg(retrieveHealth);
            consumerActor2TestKit.reply(retrieveHealthResponse);

            final var success = testKit.expectMsgClass(Status.Success.class);

            final var successMessage = "Connection test was successful.";
            assertThat(success.status()).isEqualTo(successMessage);
            Mockito.verify(connectionLogger).success(Mockito.eq(successMessage));
            Mockito.verify(genericMqttClient, Mockito.timeout(CLIENT_DISCONNECT_TIMEOUT)).disconnect();
        }
    }

    private static SubscribeResult getSourceSubscribeSuccess(final Source connectionSource) {
        final var result = Mockito.mock(SubscribeResult.class);
        Mockito.when(result.isSuccess()).thenReturn(true);
        Mockito.when(result.getMqttPublishSourceOrThrow()).thenReturn(akka.stream.javadsl.Source.empty());
        Mockito.when(result.getConnectionSource()).thenReturn(connectionSource);
        return result;
    }

    @Test
    public void testConnectionWorksAsExpectedIfSomeFailuresBecauseOfUnspecificException() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource1Addresses = new LinkedHashSet<String>();
        connectionSource1Addresses.add("source/foo");
        connectionSource1Addresses.add("source/bar");
        Mockito.when(connectionSource1.getAddresses()).thenReturn(connectionSource1Addresses);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        final var childActorNanny = Mockito.mock(ChildActorNanny.class);
        final var mqttSubscriber = Mockito.mock(MqttSubscriber.class);
        final var mqttSubscribeException = new MqttSubscribeException("Subscribing failed", null);
        Mockito.when(mqttSubscriber.subscribeForConnectionSources(Mockito.any()))
                .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                        getSourceSubscribeFailure(connectionSource1, mqttSubscribeException),
                        getSourceSubscribeSuccess(connectionSource2)
                )));
        try (
                final var childActorNannyMock = Mockito.mockStatic(ChildActorNanny.class);
                final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)
        ) {
            childActorNannyMock.when(() -> ChildActorNanny.newInstance(Mockito.any(), Mockito.any()))
                    .thenReturn(childActorNanny);
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

            final var publisherActorTestKit = actorSystemResource.newTestKit();
            Mockito.when(childActorNanny.startChildActorConflictFree(Mockito.eq(MqttPublisherActor.class.getSimpleName()),
                            Mockito.any(Props.class)))
                    .thenReturn(publisherActorTestKit.getRef());

            final var underTest = TestActorRef.apply(connectionTesterActorProps, actorSystemResource.getActorSystem());
            final var testKit = actorSystemResource.newTestKit();

            underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

            final var failure = testKit.expectMsgClass(Status.Failure.class);

            final var errorMessage =
                    MessageFormat.format("[{0}: {1}]", mqttSubscribeException.getMessage(), connectionSource1Addresses);
            assertThat(failure.cause())
                    .isInstanceOf(MqttSubscribeException.class)
                    .hasMessage(errorMessage)
                    .hasNoCause();
            Mockito.verify(connectionLogger).failure("Connection test failed: {0}", errorMessage);
            Mockito.verify(genericMqttClient, Mockito.timeout(CLIENT_DISCONNECT_TIMEOUT)).disconnect();
        }
    }

    private static SubscribeResult getSourceSubscribeFailure(final Source connectionSource,
            final MqttSubscribeException mqttSubscribeException) {

        final var result = Mockito.mock(SubscribeResult.class);
        Mockito.when(result.isSuccess()).thenReturn(false);
        Mockito.when(result.getMqttPublishSourceOrThrow()).thenThrow(new IllegalStateException("yo"));
        Mockito.when(result.getErrorOrThrow()).thenReturn(mqttSubscribeException);
        Mockito.when(result.getConnectionSource()).thenReturn(connectionSource);
        return result;
    }

    @Test
    public void testConnectionWorksAsExpectedIfSomeFailuresBecauseOfAllSubscriptionsFailedException() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        final var childActorNanny = Mockito.mock(ChildActorNanny.class);
        final var mqttSubscriber = Mockito.mock(MqttSubscriber.class);
        final var allSubscriptionsFailedException = new AllSubscriptionsFailedException(
                List.of(
                        SubscriptionStatus.newInstance(MqttTopicFilter.of("source/foo"),
                                GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(Mqtt3SubAckReturnCode.FAILURE)),
                        SubscriptionStatus.newInstance(MqttTopicFilter.of("source/bar"),
                                GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(Mqtt5SubAckReasonCode.QUOTA_EXCEEDED))
                ),
                null
        );
        Mockito.when(mqttSubscriber.subscribeForConnectionSources(Mockito.any()))
                .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                        getSourceSubscribeFailure(connectionSource1, allSubscriptionsFailedException),
                        getSourceSubscribeSuccess(connectionSource2)
                )));
        try (
                final var childActorNannyMock = Mockito.mockStatic(ChildActorNanny.class);
                final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)
        ) {
            childActorNannyMock.when(() -> ChildActorNanny.newInstance(Mockito.any(), Mockito.any()))
                    .thenReturn(childActorNanny);
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

            final var publisherActorTestKit = actorSystemResource.newTestKit();
            Mockito.when(childActorNanny.startChildActorConflictFree(Mockito.eq(MqttPublisherActor.class.getSimpleName()),
                            Mockito.any(Props.class)))
                    .thenReturn(publisherActorTestKit.getRef());

            final var underTest = TestActorRef.apply(connectionTesterActorProps, actorSystemResource.getActorSystem());
            final var testKit = actorSystemResource.newTestKit();

            underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

            final var errorMessage = MessageFormat.format("{0}",
                    allSubscriptionsFailedException.failedSubscriptionStatuses().collect(Collectors.toList()));
            final var failure = testKit.expectMsgClass(Status.Failure.class);
            assertThat(failure.cause())
                    .isInstanceOf(MqttSubscribeException.class)
                    .hasMessage(errorMessage)
                    .hasNoCause();
            Mockito.verify(connectionLogger).failure("Connection test failed: {0}", errorMessage);
            Mockito.verify(genericMqttClient, Mockito.timeout(CLIENT_DISCONNECT_TIMEOUT)).disconnect();
        }
    }

    @Test
    public void testConnectionFailsIfPublisherActorDoesNotRespond() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        final var childActorNanny = Mockito.mock(ChildActorNanny.class);
        final var mqttSubscriber = Mockito.mock(MqttSubscriber.class);
        Mockito.when(mqttSubscriber.subscribeForConnectionSources(Mockito.any()))
                .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                        getSourceSubscribeSuccess(connectionSource1),
                        getSourceSubscribeSuccess(connectionSource2)
                )));
        try (
                final var childActorNannyMock = Mockito.mockStatic(ChildActorNanny.class);
                final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)
        ) {
            childActorNannyMock.when(() -> ChildActorNanny.newInstance(Mockito.any(), Mockito.any()))
                    .thenReturn(childActorNanny);
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

            final var publisherActorTestKit = actorSystemResource.newTestKit();
            Mockito.when(childActorNanny.startChildActorConflictFree(Mockito.eq(MqttPublisherActor.class.getSimpleName()),
                            Mockito.any(Props.class)))
                    .thenReturn(publisherActorTestKit.getRef());
            final var consumerActor1TestKit = actorSystemResource.newTestKit();
            final var consumerActor2TestKit = actorSystemResource.newTestKit();
            Mockito.when(childActorNanny.startChildActorConflictFree(Mockito.eq(MqttConsumerActor.class.getSimpleName()),
                            Mockito.any(Props.class)))
                    .thenReturn(consumerActor1TestKit.getRef())
                    .thenReturn(consumerActor2TestKit.getRef());

            final var underTest = TestActorRef.apply(connectionTesterActorProps, actorSystemResource.getActorSystem());
            final var testKit = actorSystemResource.newTestKit();

            underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

            final var retrieveHealth = RetrieveHealth.newInstance();
            final var retrieveHealthResponse = RetrieveHealthResponse.of(StatusInfo.fromStatus(StatusInfo.Status.UP),
                    retrieveHealth.getDittoHeaders());
            publisherActorTestKit.expectMsg(retrieveHealth);
            consumerActor1TestKit.expectMsg(retrieveHealth);
            consumerActor1TestKit.reply(retrieveHealthResponse);
            consumerActor2TestKit.expectMsg(retrieveHealth);
            consumerActor2TestKit.reply(retrieveHealthResponse);

            final var failure = testKit.expectMsgClass(ConnectionTesterActor.ASK_TIMEOUT.plusSeconds(1L),
                    Status.Failure.class);

            assertThat(failure.cause())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageEndingWith("did not report its status within <%s>.", ConnectionTesterActor.ASK_TIMEOUT)
                    .hasCauseInstanceOf(AskTimeoutException.class);
            Mockito.verify(connectionLogger).failure(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(genericMqttClient, Mockito.timeout(CLIENT_DISCONNECT_TIMEOUT)).disconnect();
        }
    }

    @Test
    public void testConnectionFailsIfPublisherActorSendsUnexpectedResponseAndOneSubscriberActorRespondsWithStateDown() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        final var childActorNanny = Mockito.mock(ChildActorNanny.class);
        final var mqttSubscriber = Mockito.mock(MqttSubscriber.class);
        Mockito.when(mqttSubscriber.subscribeForConnectionSources(Mockito.any()))
                .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                        getSourceSubscribeSuccess(connectionSource1),
                        getSourceSubscribeSuccess(connectionSource2)
                )));
        try (
                final var childActorNannyMock = Mockito.mockStatic(ChildActorNanny.class);
                final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)
        ) {
            childActorNannyMock.when(() -> ChildActorNanny.newInstance(Mockito.any(), Mockito.any()))
                    .thenReturn(childActorNanny);
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

            final var publisherActorTestKit = actorSystemResource.newTestKit();
            Mockito.when(childActorNanny.startChildActorConflictFree(Mockito.eq(MqttPublisherActor.class.getSimpleName()),
                            Mockito.any(Props.class)))
                    .thenReturn(publisherActorTestKit.getRef());
            final var consumerActor1TestKit = actorSystemResource.newTestKit();
            final var consumerActor2TestKit = actorSystemResource.newTestKit();
            Mockito.when(childActorNanny.startChildActorConflictFree(Mockito.eq(MqttConsumerActor.class.getSimpleName()),
                            Mockito.any(Props.class)))
                    .thenReturn(consumerActor1TestKit.getRef())
                    .thenReturn(consumerActor2TestKit.getRef());

            final var underTest = TestActorRef.apply(connectionTesterActorProps, actorSystemResource.getActorSystem());
            final var testKit = actorSystemResource.newTestKit();

            underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

            final var retrieveHealth = RetrieveHealth.newInstance();
            publisherActorTestKit.expectMsg(retrieveHealth);
            publisherActorTestKit.reply(StatusInfo.Status.UP);
            consumerActor1TestKit.expectMsg(retrieveHealth);
            consumerActor1TestKit.reply(RetrieveHealthResponse.of(StatusInfo.fromStatus(StatusInfo.Status.UP),
                    retrieveHealth.getDittoHeaders()));
            consumerActor2TestKit.expectMsg(retrieveHealth);
            consumerActor2TestKit.reply(RetrieveHealthResponse.of(StatusInfo.fromStatus(StatusInfo.Status.DOWN),
                    retrieveHealth.getDittoHeaders()));

            final var failure = testKit.expectMsgClass(Status.Failure.class);

            assertThat(failure.cause())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("did not respond with a RetrieveHealthResponse but with a <Status>.")
                    .hasMessageContaining("has status <%s>", StatusInfo.Status.DOWN)
                    .hasNoCause();
            Mockito.verify(connectionLogger).failure(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(genericMqttClient, Mockito.timeout(CLIENT_DISCONNECT_TIMEOUT)).disconnect();
        }
    }

}
