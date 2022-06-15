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
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.AllSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.HiveMqttClientProperties;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttClientConnectException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.NoMqttConnectionException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SomeSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SubscriptionStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming.MqttConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.MqttPublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscriber;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscribeResult;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
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

    @Mock private static MockedStatic<GenericMqttClientFactory> genericMqttClientFactory;

    @Mock private GenericMqttClient genericMqttClient;
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
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(connection, mqttConfig))
                .withSshTunnelStateSupplier(SshTunnelState::disabled)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(UUID.randomUUID())
                .build();

        Mockito.when(genericMqttClient.connect(Mockito.any(GenericMqttConnect.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        genericMqttClientFactory.when(
                        () -> GenericMqttClientFactory.getGenericMqttClientForConnectionTesting(Mockito.any()))
                .thenReturn(genericMqttClient);

        childActorNanny = ChildActorNanny.newInstance(actorRefFactory, Mockito.mock(LoggingAdapter.class));
    }

    @Test
    public void buildInstanceWithNullHiveMqttClientPropertiesThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withInboundMappingSink(inboundMappingSink)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withChildActorNanny(childActorNanny)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .asTest()
                        .build())
                .withMessage("The hiveMqttClientProperties must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullInboundMappingSinkThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withChildActorNanny(childActorNanny)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .asTest()
                        .build())
                .withMessage("The inboundMappingSink must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullConnectivityStatusResolverThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withInboundMappingSink(inboundMappingSink)
                        .withChildActorNanny(childActorNanny)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .asTest()
                        .build())
                .withMessage("The connectivityStatusResolver must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullChildActorNannyThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withInboundMappingSink(inboundMappingSink)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withActorSystemProvider(actorSystemResource.getActorSystem())
                        .asTest()
                        .build())
                .withMessage("The childActorNanny must not be null!")
                .withNoCause();
    }

    @Test
    public void buildInstanceWithNullSystemProviderThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ConnectionTester.builder()
                        .withHiveMqttClientProperties(hiveMqttClientProperties)
                        .withInboundMappingSink(inboundMappingSink)
                        .withConnectivityStatusResolver(connectivityStatusResolver)
                        .withChildActorNanny(childActorNanny)
                        .asTest()
                        .build())
                .withMessage("The systemProvider must not be null!")
                .withNoCause();
    }

    @Test
    public void testConnectionWhenGettingGenericMqttClientFailsReturnsFailureStatus() {
        final var error = new IllegalArgumentException("Some argument is invalid.");
        Mockito.when(GenericMqttClientFactory.getGenericMqttClientForConnectionTesting(Mockito.any())).thenThrow(error);

        final var underTest = ConnectionTester.builder()
                .withHiveMqttClientProperties(hiveMqttClientProperties)
                .withInboundMappingSink(inboundMappingSink)
                .withConnectivityStatusResolver(connectivityStatusResolver)
                .withChildActorNanny(childActorNanny)
                .withActorSystemProvider(actorSystemResource.getActorSystem())
                .withCorrelationId(testNameCorrelationId.getCorrelationId())
                .asTest()
                .build();

        final var statusCompletionStage = underTest.testConnection();

        assertThat(statusCompletionStage).isCompletedWithValue(new Status.Failure(error));
        Mockito.verifyNoInteractions(actorRefFactory);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.eq(error.getMessage()));
    }

    @Test
    public void testConnectionWhenConnectingGenericMqttClientFailsReturnsFailureStatus() {
        final var error = new MqttClientConnectException("The broker is temporarily not available", null);
        Mockito.when(genericMqttClient.connect(Mockito.any(GenericMqttConnect.class)))
                .thenReturn(CompletableFuture.failedFuture(error));
        final var underTest = getDefaultConnectionTester();

        final var statusCompletionStage = underTest.testConnection();

        assertThat(statusCompletionStage)
                .succeedsWithin(Duration.ofMillis(500L))
                .isEqualTo(new Status.Failure(error));
        Mockito.verifyNoInteractions(actorRefFactory);
        Mockito.verify(connectionLogger)
                .failure(Mockito.eq("Connection test failed: {0}"), Mockito.eq(error.getMessage()));
        Mockito.verify(genericMqttClient).disconnect();
    }

    private ConnectionTester getDefaultConnectionTester() {
        return ConnectionTester.builder()
                .withHiveMqttClientProperties(hiveMqttClientProperties)
                .withInboundMappingSink(inboundMappingSink)
                .withConnectivityStatusResolver(connectivityStatusResolver)
                .withChildActorNanny(childActorNanny)
                .withActorSystemProvider(actorSystemResource.getActorSystem())
                .withCorrelationId(testNameCorrelationId.getCorrelationId())
                .asTest()
                .build();
    }

    @Test
    public void testConnectionWorksAsExpectedIfSuccessful() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        final var mqttSubscriber = Mockito.mock(MqttSubscriber.class);
        Mockito.when(mqttSubscriber.subscribeForConnectionSources(Mockito.any()))
                .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                        getSourceSubscribeSuccess(connectionSource1),
                        getSourceSubscribeSuccess(connectionSource2)
                )));
        try (final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)) {
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

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

        final var mqttSubscriber = Mockito.mock(MqttSubscriber.class);
        final var mqttSubscribeException = new MqttSubscribeException("Subscribing failed", null);
        Mockito.when(mqttSubscriber.subscribeForConnectionSources(Mockito.any()))
                .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                        getSourceSubscribeFailure(connectionSource1, mqttSubscribeException),
                        getSourceSubscribeSuccess(connectionSource2)
                )));
        try (final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)) {
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

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
        try (final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)) {
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

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
        }
    }

    @Test
    public void testConnectionWorksAsExpectedIfSomeFailuresBecauseOfSomeSubscriptionsFailedException() {
        final var connectionSource1 = Mockito.mock(Source.class);
        final var connectionSource2 = Mockito.mock(Source.class);
        Mockito.when(connection.getSources()).thenReturn(List.of(connectionSource1, connectionSource2));

        final var mqttSubscriber = Mockito.mock(MqttSubscriber.class);
        final var someSubscriptionsFailedException = new SomeSubscriptionsFailedException(List.of(
                SubscriptionStatus.newInstance(MqttTopicFilter.of("source/foo"),
                        GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(Mqtt3SubAckReturnCode.FAILURE)),
                SubscriptionStatus.newInstance(MqttTopicFilter.of("source/bar"),
                        GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(Mqtt5SubAckReasonCode.QUOTA_EXCEEDED))
        ));
        Mockito.when(mqttSubscriber.subscribeForConnectionSources(Mockito.any()))
                .thenReturn(akka.stream.javadsl.Source.fromJavaStream(() -> Stream.of(
                        getSourceSubscribeSuccess(connectionSource1),
                        getSourceSubscribeFailure(connectionSource2, someSubscriptionsFailedException)
                )));

        try (final var mqttSubscriberMock = Mockito.mockStatic(MqttSubscriber.class)) {
            mqttSubscriberMock.when(() -> MqttSubscriber.newInstance(Mockito.any())).thenReturn(mqttSubscriber);

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
        }
    }

}
