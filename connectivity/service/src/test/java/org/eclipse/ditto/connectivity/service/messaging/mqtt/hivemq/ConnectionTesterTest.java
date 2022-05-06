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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.ChildActorNanny;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttConnAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.HiveMqttClientProperties;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.MqttClientConnectException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.NoMqttConnectionException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming.MqttConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.GenericMqttPublishingClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.MqttPublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.AllSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.GenericMqttSubAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.GenericMqttSubscribingClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscriber;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SomeSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SourceSubscribeResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscribeResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscriptionStatus;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.LoggingAdapter;
import akka.stream.javadsl.Sink;

/**
 * Unit test for {@link ConnectionTester}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ConnectionTesterTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance();

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock private GenericMqttSubscribingClient genericMqttSubscribingClient;
    @Mock private GenericMqttPublishingClient genericMqttPublishingClient;
    @Mock private Connection connection;
    @Mock private ConnectivityConfig connectivityConfig;
    @Mock private ConnectionLogger connectionLogger;
    private HiveMqttClientProperties hiveMqttClientProperties;
    @Mock private Sink<Object, NotUsed> inboundMappingSink;
    @Mock private ConnectivityStatusResolver connectivityStatusResolver;
    @Mock private ActorRefFactory actorRefFactory;
    private ChildActorNanny childActorNanny;

    @Before
    public void before() throws NoMqttConnectionException {
        Mockito.when(connection.getConnectionType()).thenReturn(ConnectionType.MQTT);
        Mockito.when(connection.getId()).thenReturn(CONNECTION_ID);
        Mockito.when(connection.getUri()).thenReturn("example.com");

        final var connectionConfig = Mockito.mock(ConnectionConfig.class);
        final var mqttConfig = Mockito.mock(MqttConfig.class);
        Mockito.when(connectionConfig.getMqttConfig()).thenReturn(mqttConfig);
        Mockito.when(connectivityConfig.getConnectionConfig()).thenReturn(connectionConfig);

        hiveMqttClientProperties = HiveMqttClientProperties.builder()
                .withMqttConnection(connection)
                .withConnectivityConfig(connectivityConfig)
                .withSshTunnelStateSupplier(SshTunnelState::disabled)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(UUID.randomUUID())
                .build();

        Mockito.when(genericMqttSubscribingClient.connect(Mockito.any(GenericMqttConnect.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        GenericMqttConnAckStatus.ofMqtt3ConnAckReturnCode(Mqtt3ConnAckReturnCode.SUCCESS)
                ));
        Mockito.when(genericMqttPublishingClient.connect(Mockito.any(GenericMqttConnect.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        GenericMqttConnAckStatus.ofMqtt3ConnAckReturnCode(Mqtt3ConnAckReturnCode.SUCCESS)
                ));

        childActorNanny = ChildActorNanny.newInstance(actorRefFactory, Mockito.mock(LoggingAdapter.class));
    }

    @Test
    public void buildInstanceWithNullSubscribingClientFactoryThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withPublishingClientFactory(unused -> genericMqttPublishingClient)
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withInboundMappingSink(inboundMappingSink)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withChildActorNanny(childActorNanny)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .build())
                .withMessage("The subscribingClientFactory must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullPublishingClientFactoryThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withSubscribingClientFactory(unused -> genericMqttSubscribingClient)
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withInboundMappingSink(inboundMappingSink)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withChildActorNanny(childActorNanny)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .build())
                .withMessage("The publishingClientFactory must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullHiveMqttClientPropertiesThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withSubscribingClientFactory(unused -> genericMqttSubscribingClient)
                        .withPublishingClientFactory(unused -> genericMqttPublishingClient)
                        .withInboundMappingSink(inboundMappingSink)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withChildActorNanny(childActorNanny)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .build())
                .withMessage("The hiveMqttClientProperties must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullInboundMappingSinkThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withSubscribingClientFactory(unused -> genericMqttSubscribingClient)
                        .withPublishingClientFactory(unused -> genericMqttPublishingClient)
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withChildActorNanny(childActorNanny)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .build())
                .withMessage("The inboundMappingSink must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullConnectivityStatusResolverThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withSubscribingClientFactory(unused -> genericMqttSubscribingClient)
                        .withPublishingClientFactory(unused -> genericMqttPublishingClient)
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withInboundMappingSink(inboundMappingSink)
                        .withChildActorNanny(childActorNanny)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .build())
                .withMessage("The connectivityStatusResolver must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullChildActorNannyThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withSubscribingClientFactory(unused -> genericMqttSubscribingClient)
                        .withPublishingClientFactory(unused -> genericMqttPublishingClient)
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withInboundMappingSink(inboundMappingSink)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .build())
                .withMessage("The childActorNanny must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullSystemProviderThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withSubscribingClientFactory(unused -> genericMqttSubscribingClient)
                        .withPublishingClientFactory(unused -> genericMqttPublishingClient)
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withInboundMappingSink(inboundMappingSink)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withChildActorNanny(childActorNanny)
                        .build())
                .withMessage("The systemProvider must not be null!")
                .withNoCause();
    }

    @Test
    public void testConnectionWhenGettingSubscribingClientFailsReturnsFailureStatus() {
        final var error = new IllegalArgumentException("Some argument is invalid.");

        final var underTest = ConnectionTester.builder()
                .withSubscribingClientFactory(unused -> {
                    throw error;
                })
                .withPublishingClientFactory(unused -> genericMqttPublishingClient)
                .withHiveMqttClientProperties(hiveMqttClientProperties)
                .withInboundMappingSink(inboundMappingSink)
                .withConnectivityStatusResolver(connectivityStatusResolver)
                .withChildActorNanny(childActorNanny)
                .withActorSystemProvider(actorSystemResource.getActorSystem())
                .withCorrelationId(testNameCorrelationId.getCorrelationId())
                .build();

        final var statusCompletionStage = underTest.testConnection();

        assertThat(statusCompletionStage).isCompletedWithValue(new Status.Failure(error));
        Mockito.verifyNoInteractions(actorRefFactory, genericMqttSubscribingClient, genericMqttPublishingClient);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.eq(error.getMessage()));
    }

    @Test
    public void testConnectionWhenGettingPublishingClientFailsReturnsFailureStatus() {
        final var error = new IllegalArgumentException("Some argument is invalid.");

        final var underTest = ConnectionTester.builder()
                .withSubscribingClientFactory(unused -> genericMqttSubscribingClient)
                .withPublishingClientFactory(unused -> {
                    throw error;
                })
                .withHiveMqttClientProperties(hiveMqttClientProperties)
                .withInboundMappingSink(inboundMappingSink)
                .withConnectivityStatusResolver(connectivityStatusResolver)
                .withChildActorNanny(childActorNanny)
                .withActorSystemProvider(actorSystemResource.getActorSystem())
                .withCorrelationId(testNameCorrelationId.getCorrelationId())
                .build();

        final var statusCompletionStage = underTest.testConnection();

        assertThat(statusCompletionStage).isCompletedWithValue(new Status.Failure(error));
        Mockito.verifyNoInteractions(actorRefFactory, genericMqttPublishingClient, genericMqttPublishingClient);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.eq(error.getMessage()));
    }

    @Test
    public void testConnectionWhenConnectingSubscribingClientFailsReturnsFailureStatus() {
        final var error = new MqttClientConnectException("The broker is temporarily not available", null);
        Mockito.when(genericMqttSubscribingClient.connect(Mockito.any(GenericMqttConnect.class)))
                .thenReturn(CompletableFuture.failedFuture(error));
        final var underTest = getDefaultConnectionTester();

        final var statusCompletionStage = underTest.testConnection();

        assertThat(statusCompletionStage)
                .succeedsWithin(Duration.ofMillis(500L))
                .isEqualTo(new Status.Failure(error));
        Mockito.verifyNoInteractions(actorRefFactory);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.eq(error.getMessage()));
        Mockito.verify(genericMqttSubscribingClient).disconnect();
        Mockito.verify(genericMqttPublishingClient).disconnect();
    }

    private ConnectionTester getDefaultConnectionTester() {
        return ConnectionTester.builder()
                .withSubscribingClientFactory(unused -> genericMqttSubscribingClient)
                .withPublishingClientFactory(unused -> genericMqttPublishingClient)
                .withHiveMqttClientProperties(hiveMqttClientProperties)
                .withInboundMappingSink(inboundMappingSink)
                .withConnectivityStatusResolver(connectivityStatusResolver)
                .withChildActorNanny(childActorNanny)
                .withActorSystemProvider(actorSystemResource.getActorSystem())
                .withCorrelationId(testNameCorrelationId.getCorrelationId())
                .build();
    }

    @Test
    public void testConnectionWhenConnectingPublishingClientFailsReturnsFailureStatus() {
        final var error = new MqttClientConnectException("The broker is temporarily not available", null);
        Mockito.when(genericMqttPublishingClient.connect(Mockito.any(GenericMqttConnect.class)))
                .thenReturn(CompletableFuture.failedFuture(error));
        final var underTest = getDefaultConnectionTester();

        final var statusCompletionStage = underTest.testConnection();

        assertThat(statusCompletionStage)
                .succeedsWithin(Duration.ofMillis(500L))
                .isEqualTo(new Status.Failure(error));
        Mockito.verifyNoInteractions(actorRefFactory);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.eq(error.getMessage()));
        Mockito.verify(genericMqttPublishingClient).disconnect();
        Mockito.verify(genericMqttPublishingClient).disconnect();
    }

    @Test
    public void testConnectionWorksAsExpectedIfSuccessful() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        try (final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)) {
            mqttSubscriberMock.when(() -> MqttSubscriber.subscribeForConnectionSources(Mockito.any(), Mockito.any()))
                    .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                            getSourceSubscribeSuccess(connectionSource1),
                            getSourceSubscribeSuccess(connectionSource2)
                    )));

            final var mqttConsumerActorSimpleName = MqttConsumerActor.class.getSimpleName();
            final var consumerActor1Ref = Mockito.mock(ActorRef.class);
            Mockito.when(actorRefFactory.actorOf(Mockito.any(Props.class),
                            Mockito.eq(mqttConsumerActorSimpleName + "1")))
                    .thenReturn(consumerActor1Ref);
            final var consumerActor2Ref = Mockito.mock(ActorRef.class);
            Mockito.when(actorRefFactory.actorOf(Mockito.any(Props.class),
                            Mockito.eq(mqttConsumerActorSimpleName + "2")))
                    .thenReturn(consumerActor2Ref);

            final var publisherActor1Ref = Mockito.mock(ActorRef.class);
            final var publisherActorName = MqttPublisherActor.class.getSimpleName() + "1";
            Mockito.when(actorRefFactory.actorOf(Mockito.any(Props.class), Mockito.eq(publisherActorName)))
                    .thenReturn(publisherActor1Ref);

            final var underTest = getDefaultConnectionTester();

            final var statusCompletionStage = underTest.testConnection();

            final var successMessage = "Connection test was successful.";
            assertThat(statusCompletionStage)
                    .succeedsWithin(Duration.ofMillis(500L))
                    .isEqualTo(new Status.Success(successMessage));
            Mockito.verify(connectionLogger).success(successMessage);
            Mockito.verify(actorRefFactory).stop(Mockito.eq(consumerActor1Ref));
            Mockito.verify(actorRefFactory).stop(Mockito.eq(consumerActor2Ref));
            Mockito.verify(actorRefFactory).stop(Mockito.eq(publisherActor1Ref));
        }
    }

    private static SourceSubscribeResult getSourceSubscribeSuccess(final Source connectionSource) {
        final var subscribeSuccess = Mockito.mock(SubscribeResult.class);
        Mockito.when(subscribeSuccess.isSuccess()).thenReturn(true);
        Mockito.when(subscribeSuccess.getMqttPublishSourceOrThrow()).thenReturn(akka.stream.javadsl.Source.empty());
        return SourceSubscribeResult.newInstance(connectionSource, subscribeSuccess);
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

        try (final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)) {
            final var mqttSubscribeException = new MqttSubscribeException("Subscribing failed", null);
            mqttSubscriberMock.when(() -> MqttSubscriber.subscribeForConnectionSources(Mockito.any(), Mockito.any()))
                    .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                            getSourceSubscribeFailure(connectionSource1, mqttSubscribeException),
                            getSourceSubscribeSuccess(connectionSource2)
                    )));

            final var publisherActor1Ref = Mockito.mock(ActorRef.class);
            final var publisherActorName = MqttPublisherActor.class.getSimpleName() + "1";
            Mockito.when(actorRefFactory.actorOf(Mockito.any(Props.class), Mockito.eq(publisherActorName)))
                    .thenReturn(publisherActor1Ref);

            final var underTest = getDefaultConnectionTester();

            final var statusCompletionStage = underTest.testConnection();

            final var errorMessage =
                    MessageFormat.format("[{0}: {1}]", mqttSubscribeException.getMessage(), connectionSource1Addresses);
            assertThat(statusCompletionStage)
                    .succeedsWithin(Duration.ofMillis(500L))
                    .isInstanceOfSatisfying(Status.Failure.class,
                            failure -> assertThat(failure.cause())
                                    .isInstanceOf(MqttSubscribeException.class)
                                    .hasMessage(errorMessage));
            Mockito.verify(connectionLogger).failure("Connection test failed: {0}", errorMessage);
            Mockito.verify(actorRefFactory).stop(Mockito.eq(publisherActor1Ref));
        }
    }

    private static SourceSubscribeResult getSourceSubscribeFailure(final Source connectionSource,
            final MqttSubscribeException mqttSubscribeException) {

        final var subscribeSuccess = Mockito.mock(SubscribeResult.class);
        Mockito.when(subscribeSuccess.isSuccess()).thenReturn(false);
        Mockito.when(subscribeSuccess.getMqttPublishSourceOrThrow()).thenThrow(new IllegalStateException("yo"));
        Mockito.when(subscribeSuccess.getErrorOrThrow()).thenReturn(mqttSubscribeException);
        return SourceSubscribeResult.newInstance(connectionSource, subscribeSuccess);
    }

    @Test
    public void testConnectionWorksAsExpectedIfSomeFailuresBecauseOfAllSubscriptionsFailedException() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        try (final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)) {
            final var allSubscriptionsFailedException = new AllSubscriptionsFailedException(List.of(
                    new SubscriptionStatus(MqttTopicFilter.of("source/foo"),
                            GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(Mqtt3SubAckReturnCode.FAILURE)),
                    new SubscriptionStatus(MqttTopicFilter.of("source/bar"),
                            GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(Mqtt5SubAckReasonCode.QUOTA_EXCEEDED))
            ));
            mqttSubscriberMock.when(() -> MqttSubscriber.subscribeForConnectionSources(Mockito.any(), Mockito.any()))
                    .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                            getSourceSubscribeFailure(connectionSource1, allSubscriptionsFailedException),
                            getSourceSubscribeSuccess(connectionSource2)
                    )));

            final var publisherActor1Ref = Mockito.mock(ActorRef.class);
            final var publisherActorName = MqttPublisherActor.class.getSimpleName() + "1";
            Mockito.when(actorRefFactory.actorOf(Mockito.any(Props.class), Mockito.eq(publisherActorName)))
                    .thenReturn(publisherActor1Ref);

            final var underTest = getDefaultConnectionTester();

            final var statusCompletionStage = underTest.testConnection();

            final var errorMessage =
                    MessageFormat.format("{0}", allSubscriptionsFailedException.getFailedSubscriptionStatuses());
            assertThat(statusCompletionStage)
                    .succeedsWithin(Duration.ofMillis(500L))
                    .isInstanceOfSatisfying(Status.Failure.class,
                            failure -> assertThat(failure.cause())
                                    .isInstanceOf(MqttSubscribeException.class)
                                    .hasMessage(errorMessage));
            Mockito.verify(connectionLogger).failure("Connection test failed: {0}", errorMessage);
            Mockito.verify(actorRefFactory).stop(Mockito.eq(publisherActor1Ref));
        }
    }

    @Test
    public void testConnectionWorksAsExpectedIfSomeFailuresBecauseOfSomeSubscriptionsFailedException() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        try (final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)) {
            final var someSubscriptionsFailedException = new SomeSubscriptionsFailedException(List.of(
                    new SubscriptionStatus(MqttTopicFilter.of("source/foo"),
                            GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(Mqtt3SubAckReturnCode.FAILURE)),
                    new SubscriptionStatus(MqttTopicFilter.of("source/bar"),
                            GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(Mqtt5SubAckReasonCode.QUOTA_EXCEEDED))
            ));
            mqttSubscriberMock.when(() -> MqttSubscriber.subscribeForConnectionSources(Mockito.any(), Mockito.any()))
                    .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                            getSourceSubscribeSuccess(connectionSource1),
                            getSourceSubscribeFailure(connectionSource2, someSubscriptionsFailedException)
                    )));

            final var publisherActor1Ref = Mockito.mock(ActorRef.class);
            final var publisherActorName = MqttPublisherActor.class.getSimpleName() + "1";
            Mockito.when(actorRefFactory.actorOf(Mockito.any(Props.class), Mockito.eq(publisherActorName)))
                    .thenReturn(publisherActor1Ref);

            final var underTest = getDefaultConnectionTester();

            final var statusCompletionStage = underTest.testConnection();

            final var errorMessage =
                    MessageFormat.format("{0}", someSubscriptionsFailedException.getFailedSubscriptionStatuses());
            assertThat(statusCompletionStage)
                    .succeedsWithin(Duration.ofMillis(500L))
                    .isInstanceOfSatisfying(Status.Failure.class,
                            failure -> assertThat(failure.cause())
                                    .isInstanceOf(MqttSubscribeException.class)
                                    .hasMessage(errorMessage));
            Mockito.verify(connectionLogger).failure("Connection test failed: {0}", errorMessage);
            Mockito.verify(actorRefFactory).stop(Mockito.eq(publisherActor1Ref));
        }
    }

}