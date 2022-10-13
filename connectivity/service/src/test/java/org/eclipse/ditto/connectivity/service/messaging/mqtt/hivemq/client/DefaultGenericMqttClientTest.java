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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.mqtt.IllegalReceiveMaximumValueException;
import org.eclipse.ditto.connectivity.model.mqtt.ReceiveMaximum;
import org.eclipse.ditto.connectivity.model.mqtt.SessionExpiryInterval;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.IllegalKeepAliveIntervalSecondsException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.KeepAliveInterval;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscription;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Unit test for {@link DefaultGenericMqttClient}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultGenericMqttClientTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();

    @Mock private BaseGenericMqttSubscribingClient<?> subscribingClient;
    @Mock private BaseGenericMqttPublishingClient<?> publishingClient;
    @Mock private MqttConfig mqttConfig;
    @Mock private MqttSpecificConfig mqttSpecificConfig;
    @Mock private HiveMqttClientProperties hiveMqttClientProperties;

    @Before
    public void before() throws IllegalKeepAliveIntervalSecondsException {
        Mockito.when(hiveMqttClientProperties.getConnectionId()).thenReturn(CONNECTION_ID);
        Mockito.when(hiveMqttClientProperties.getMqttConfig()).thenReturn(mqttConfig);
        Mockito.when(hiveMqttClientProperties.getMqttSpecificConfig()).thenReturn(mqttSpecificConfig);
    }

    @Test
    public void newInstanceWithNullGenericMqttSubscribingClientThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> DefaultGenericMqttClient.newInstance(null,
                        publishingClient,
                        hiveMqttClientProperties))
                .withMessage("The genericMqttSubscribingClient must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullGenericMqttPublishingClientThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> DefaultGenericMqttClient.newInstance(subscribingClient,
                        null,
                        hiveMqttClientProperties))
                .withMessage("The genericMqttPublishingClient must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullHiveMqttClientPropertiesThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, null))
                .withMessage("The hiveMqttClientProperties must not be null!")
                .withNoCause();
    }

    @Test
    public void connectWithoutArgumentWorksAsExpectedIfConfiguredKeepAliveIntervalIsValid()
            throws IllegalKeepAliveIntervalSecondsException, IllegalReceiveMaximumValueException {

        Mockito.when(subscribingClient.connect(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(publishingClient.connect(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        final var sessionExpiryInterval = SessionExpiryInterval.defaultSessionExpiryInterval();
        Mockito.when(mqttConfig.getSessionExpiryInterval()).thenReturn(sessionExpiryInterval);
        final var receiveMaximum = ReceiveMaximum.of(22_000);
        Mockito.when(mqttConfig.getClientReceiveMaximum()).thenReturn(receiveMaximum);
        final var cleanSession = true;
        final var keepAliveInterval = KeepAliveInterval.of(Duration.ofSeconds(23L));
        Mockito.when(mqttSpecificConfig.cleanSession()).thenReturn(cleanSession);
        Mockito.when(mqttSpecificConfig.getKeepAliveIntervalOrDefault()).thenReturn(keepAliveInterval);
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);
        final var genericMqttConnect = GenericMqttConnect.newInstance(cleanSession,
                keepAliveInterval,
                sessionExpiryInterval,
                receiveMaximum);

        underTest.connect();

        Mockito.verify(subscribingClient).connect(Mockito.eq(genericMqttConnect));
        Mockito.verify(publishingClient).connect(Mockito.eq(genericMqttConnect));
    }

    @Test
    public void connectWithoutArgumentReturnsFailedCompletionStageIfConfiguredKeepAliveIntervalIsInvalid() {
        final var connection = Mockito.mock(Connection.class);
        final var invalidKeepAliveIntervalSeconds = "-1";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("keepAlive", invalidKeepAliveIntervalSeconds));
        final var mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection, Mockito.mock(MqttConfig.class));
        Mockito.when(hiveMqttClientProperties.getMqttSpecificConfig()).thenReturn(mqttSpecificConfig);
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var connectFuture = underTest.connect();

        assertThat(connectFuture)
                .failsWithin(Duration.ofMillis(500L))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalKeepAliveIntervalSecondsException.class)
                .withMessageContaining(invalidKeepAliveIntervalSeconds);
    }

    @Test
    public void connectWithNullGenericMqttConnectThrowsException() {
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> underTest.connect(null))
                .withMessage("The genericMqttConnect must not be null!")
                .withNoCause();
    }

    @Test
    public void connectCallsConnectOnSubscribingClient() {
        Mockito.when(subscribingClient.connect(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        final var genericMqttConnect = GenericMqttConnect.newInstance(false,
                KeepAliveInterval.defaultKeepAlive(),
                SessionExpiryInterval.defaultSessionExpiryInterval(),
                ReceiveMaximum.defaultReceiveMaximum());
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        underTest.connect(genericMqttConnect);

        Mockito.verify(subscribingClient).connect(Mockito.eq(genericMqttConnect));
        Mockito.verify(publishingClient).connect(Mockito.any());
        Mockito.verify(publishingClient, Mockito.never()).disconnect();
        Mockito.verify(subscribingClient, Mockito.never()).disconnect();
    }

    @Test
    public void connectCallsConnectOnPublishingClientIfConnectingSubscribingClientSucceeded() {
        Mockito.when(subscribingClient.connect(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(publishingClient.connect(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        final var genericMqttConnect = GenericMqttConnect.newInstance(false,
                KeepAliveInterval.defaultKeepAlive(),
                SessionExpiryInterval.defaultSessionExpiryInterval(),
                ReceiveMaximum.defaultReceiveMaximum());
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        underTest.connect(genericMqttConnect);

        Mockito.verify(publishingClient).connect(Mockito.eq(genericMqttConnect));
        Mockito.verify(publishingClient, Mockito.never()).disconnect();
        Mockito.verify(subscribingClient, Mockito.never()).disconnect();
    }

    @Test
    public void connectDoesNotCallConnectOnPublishingClientIfConnectingSubscribingClientFailed() {
        final var illegalStateException = new IllegalStateException("Yolo!");
        Mockito.when(subscribingClient.connect(Mockito.any()))
                .thenReturn(CompletableFuture.failedFuture(illegalStateException));
        final var genericMqttConnect = GenericMqttConnect.newInstance(false,
                KeepAliveInterval.defaultKeepAlive(),
                SessionExpiryInterval.defaultSessionExpiryInterval(),
                ReceiveMaximum.defaultReceiveMaximum());
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var connectFuture = underTest.connect(genericMqttConnect);

        assertThat(connectFuture)
                .failsWithin(Duration.ofSeconds(1L))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isEqualTo(illegalStateException);

        Mockito.verify(publishingClient, Mockito.never()).connect(Mockito.eq(genericMqttConnect));
        Mockito.verify(publishingClient, Mockito.never()).disconnect();
        Mockito.verify(subscribingClient, Mockito.never()).disconnect();
    }

    @Test
    public void connectDisconnectsSubscribingClientIfConnectingPublishingClientFailed() {
        Mockito.when(subscribingClient.connect(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        final var illegalStateException = new IllegalStateException("Yolo!");
        Mockito.when(publishingClient.connect(Mockito.any()))
                .thenReturn(CompletableFuture.failedFuture(illegalStateException));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var connectFuture = underTest.connect(GenericMqttConnect.newInstance(false,
                KeepAliveInterval.defaultKeepAlive(),
                SessionExpiryInterval.defaultSessionExpiryInterval(),
                ReceiveMaximum.defaultReceiveMaximum()));

        assertThat(connectFuture)
                .failsWithin(Duration.ofSeconds(1L))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isEqualTo(illegalStateException);

        Mockito.verify(publishingClient, Mockito.never()).disconnect();
        Mockito.verify(subscribingClient).disconnect();
    }

    @Test
    public void disconnectCallsDisconnectOnSubscribingClientAndPublishingClient() {
        Mockito.when(subscribingClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(publishingClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var disconnectFuture = underTest.disconnect();

        assertThat(disconnectFuture).succeedsWithin(Duration.ofSeconds(1L));

        Mockito.verify(subscribingClient).disconnect();
        Mockito.verify(publishingClient).disconnect();
    }

    @Test
    public void disconnectFailsIfDisconnectOnSubscribingClientFails() {
        final var illegalStateException = new IllegalStateException("Yolo!");
        Mockito.when(subscribingClient.disconnect()).thenReturn(CompletableFuture.failedFuture(illegalStateException));
        Mockito.when(publishingClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var disconnectFuture = underTest.disconnect();

        assertThat(disconnectFuture)
                .failsWithin(Duration.ofSeconds(1L))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isEqualTo(illegalStateException);
    }

    @Test
    public void disconnectFailsIfDisconnectOnPublishingClientFails() {
        Mockito.when(subscribingClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        final var illegalStateException = new IllegalStateException("Yolo!");
        Mockito.when(publishingClient.disconnect()).thenReturn(CompletableFuture.failedFuture(illegalStateException));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var disconnectFuture = underTest.disconnect();

        assertThat(disconnectFuture)
                .failsWithin(Duration.ofSeconds(1L))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isEqualTo(illegalStateException);
    }

    @Test
    public void disconnectClientRoleWithNullThrowsException() {
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> underTest.disconnectClientRole(null))
                .withMessage("The clientRole must not be null!")
                .withNoCause();
    }

    @Test
    public void disconnectClientRoleWithConsumerRoleDisconnectsOnlySubscribingClient() {
        Mockito.when(subscribingClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var disconnectFuture = underTest.disconnectClientRole(ClientRole.CONSUMER);

        assertThat(disconnectFuture).succeedsWithin(Duration.ofSeconds(1L));

        Mockito.verify(subscribingClient).disconnect();
        Mockito.verify(publishingClient, Mockito.never()).disconnect();
    }

    @Test
    public void disconnectClientRoleWithPublisherRoleDisconnectsOnlyPublishingClient() {
        Mockito.when(publishingClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var disconnectFuture = underTest.disconnectClientRole(ClientRole.PUBLISHER);

        assertThat(disconnectFuture).succeedsWithin(Duration.ofSeconds(1L));

        Mockito.verify(subscribingClient, Mockito.never()).disconnect();
        Mockito.verify(publishingClient).disconnect();
    }

    @Test
    public void disconnectClientRoleWithConsumerPublisherRoleDisconnectsPublishingClientAndSubscribingClient() {
        Mockito.when(subscribingClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(publishingClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var disconnectFuture = underTest.disconnectClientRole(ClientRole.CONSUMER_PUBLISHER);

        assertThat(disconnectFuture).succeedsWithin(Duration.ofSeconds(1L));

        Mockito.verify(subscribingClient).disconnect();
        Mockito.verify(publishingClient).disconnect();
    }

    @Test
    public void subscribeCallsSubscribeOnSubscribingClient() {
        final var genericMqttSubscribe = GenericMqttSubscribe.of(
                Set.of(GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/status"), MqttQos.AT_LEAST_ONCE))
        );
        final var genericMqttSubAck = Mockito.mock(GenericMqttSubAck.class);
        Mockito.when(subscribingClient.subscribe(Mockito.eq(genericMqttSubscribe)))
                .thenReturn(Single.just(genericMqttSubAck));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var genericMqttSubAckSingle = underTest.subscribe(genericMqttSubscribe);

        assertThat(subscribeForSingleValueResponse(genericMqttSubAckSingle))
                .succeedsWithin(Duration.ofSeconds(1L))
                .isEqualTo(genericMqttSubAck);
        Mockito.verifyNoInteractions(publishingClient);
    }

    private static <T> CompletableFuture<T> subscribeForSingleValueResponse(final Single<T> singleValueResponse) {
        final var result = new CompletableFuture<T>();
        singleValueResponse.subscribe(result::complete, result::completeExceptionally);
        return result;
    }

    @Test
    public void consumeSubscribedPublishesWithManualAcknowledgementDelegatesToSubscribingClient() {
        final var genericMqttPublishes = List.of(
                GenericMqttPublish.builder(MqttTopic.of("source/status"), MqttQos.AT_LEAST_ONCE)
                        .payload(ByteBufferUtils.fromUtf8String("offline"))
                        .build(),
                GenericMqttPublish.builder(MqttTopic.of("source/status"), MqttQos.AT_LEAST_ONCE)
                        .payload(ByteBufferUtils.fromUtf8String("maybe online"))
                        .build(),
                GenericMqttPublish.builder(MqttTopic.of("source/status"), MqttQos.AT_LEAST_ONCE)
                        .payload(ByteBufferUtils.fromUtf8String("definitively online"))
                        .build()
        );
        Mockito.when(subscribingClient.consumeSubscribedPublishesWithManualAcknowledgement())
                .thenReturn(Flowable.fromIterable(genericMqttPublishes));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        final var genericMqttMqttPublishFlowable = underTest.consumeSubscribedPublishesWithManualAcknowledgement();

        assertThat(subscribeForFlowableValueResponse(genericMqttMqttPublishFlowable))
                .succeedsWithin(Duration.ofSeconds(1L))
                .isEqualTo(genericMqttPublishes);
        Mockito.verifyNoInteractions(publishingClient);
    }

    private static <T> CompletableFuture<List<T>> subscribeForFlowableValueResponse(
            final Flowable<T> flowableValueResponse
    ) {
        final var result = new CompletableFuture<List<T>>();
        final var elements = new ArrayList<T>();
        flowableValueResponse.subscribe(elements::add, result::completeExceptionally, () -> result.complete(elements));
        return result;
    }

    @Test
    public void publishDelegatesToPublishingClient() {
        final var genericMqttPublish = GenericMqttPublish.builder(MqttTopic.of("target/status"), MqttQos.AT_MOST_ONCE)
                .payload(ByteBufferUtils.fromUtf8String("offline"))
                .retain(true)
                .build();
        final var genericMqttPublishResult = GenericMqttPublishResult.success(genericMqttPublish);
        Mockito.when(publishingClient.publish(Mockito.eq(genericMqttPublish)))
                .thenReturn(CompletableFuture.completedFuture(genericMqttPublishResult));
        final var underTest =
                DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);

        assertThat(underTest.publish(genericMqttPublish))
                .succeedsWithin(Duration.ofSeconds(1L))
                .isEqualTo(genericMqttPublishResult);
        Mockito.verifyNoInteractions(subscribingClient);
    }

}