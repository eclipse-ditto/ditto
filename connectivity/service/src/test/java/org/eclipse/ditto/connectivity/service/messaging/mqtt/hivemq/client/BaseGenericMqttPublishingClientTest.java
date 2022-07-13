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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientConfig;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientConfig;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;

/**
 * Unit test for {@link BaseGenericMqttPublishingClient}.
 */
@RunWith(Enclosed.class)
public final class BaseGenericMqttPublishingClientTest {

    @RunWith(MockitoJUnitRunner.class)
    public static final class Mqtt3AsyncClientTest {

        private static final Mqtt3Publish MQTT_3_PUBLISH = Mockito.mock(Mqtt3Publish.class);
        private static final GenericMqttPublish GENERIC_MQTT_PUBLISH =
                GenericMqttPublish.ofMqtt3Publish(MQTT_3_PUBLISH);

        @Mock
        private Mqtt3AsyncClient mqtt3AsyncClient;

        @Before
        public void before() {
            Mockito.when(mqtt3AsyncClient.publish(Mockito.eq(MQTT_3_PUBLISH)))
                    .thenReturn(CompletableFuture.completedFuture(MQTT_3_PUBLISH));
        }

        @Test
        public void ofMqtt3AsyncClientWithNullMqtt3AsyncClientThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(null, ClientRole.PUBLISHER))
                    .withMessage("The mqtt3AsyncClient must not be null!")
                    .withNoCause();
        }

        @Test
        public void ofMqtt3AsyncClientWithNullClientRoleThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient, null))
                    .withMessage("The clientRole must not be null!")
                    .withNoCause();
        }

        @Test
        public void publishWithNullThrowsException() {
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient, ClientRole.PUBLISHER);

            assertThatNullPointerException()
                    .isThrownBy(() -> underTest.publish(null))
                    .withMessage("The genericMqttPublish must not be null!")
                    .withNoCause();
        }

        @Test
        public void publishIsDelegatedToMqtt3AsyncClient() {
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient, ClientRole.PUBLISHER);

            underTest.publish(GENERIC_MQTT_PUBLISH);

            Mockito.verify(mqtt3AsyncClient).publish(Mockito.eq(MQTT_3_PUBLISH));
        }

        @Test
        public void sendPublishReturnsCompletedStageWithExpectedSuccessResultIfNoErrorOccurred() {
            Mockito.when(mqtt3AsyncClient.publish(Mockito.eq(MQTT_3_PUBLISH)))
                    .thenReturn(CompletableFuture.completedFuture(MQTT_3_PUBLISH));
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient, ClientRole.PUBLISHER);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.success(GENERIC_MQTT_PUBLISH));
        }

        @Test
        public void sendPublishReturnsFailedStageWithExpectedErrorIfErrorOccurred() {
            final var error = new IllegalStateException("This is totally expected.");
            Mockito.when(mqtt3AsyncClient.publish(Mockito.eq(MQTT_3_PUBLISH)))
                    .thenReturn(CompletableFuture.failedFuture(error));
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient, ClientRole.PUBLISHER);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, error));
        }

        @Test
        public void toStringReturnsExpected() {
            final var mqttClientId = "Anneliese";
            final var mqtt3ClientConfig = Mockito.mock(Mqtt3ClientConfig.class);
            Mockito.when(mqtt3ClientConfig.getClientIdentifier())
                    .thenReturn(Optional.of(MqttClientIdentifier.of(mqttClientId)));
            Mockito.when(mqtt3AsyncClient.getConfig()).thenReturn(mqtt3ClientConfig);
            final var clientRole = ClientRole.PUBLISHER;
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient, clientRole);

            assertThat(underTest).hasToString(clientRole + ":" + mqttClientId);
        }

    }

    @RunWith(MockitoJUnitRunner.class)
    public static final class Mqtt5AsyncClientTest {

        private static final Mqtt5Publish MQTT_5_PUBLISH = Mockito.mock(Mqtt5Publish.class);
        private static final GenericMqttPublish GENERIC_MQTT_PUBLISH =
                GenericMqttPublish.ofMqtt5Publish(MQTT_5_PUBLISH);

        @Mock private Mqtt5AsyncClient mqtt5AsyncClient;
        @Mock private Mqtt5PublishResult mqtt5PublishResult;

        @Before
        public void before() {
            Mockito.when(mqtt5AsyncClient.publish(Mockito.eq(MQTT_5_PUBLISH)))
                    .thenReturn(CompletableFuture.completedFuture(mqtt5PublishResult));
        }

        @Test
        public void ofMqtt5AsyncClientWithNullMqtt5AsyncClientThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(null, ClientRole.PUBLISHER))
                    .withMessage("The mqtt5AsyncClient must not be null!")
                    .withNoCause();
        }

        @Test
        public void ofMqtt5AsyncClientWithNullClientRoleThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient, null))
                    .withMessage("The clientRole must not be null!")
                    .withNoCause();
        }

        @Test
        public void publishWithNullThrowsException() {
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient, ClientRole.PUBLISHER);

            assertThatNullPointerException()
                    .isThrownBy(() -> underTest.publish(null))
                    .withMessage("The genericMqttPublish must not be null!")
                    .withNoCause();
        }

        @Test
        public void publishIsDelegatedToMqtt5AsyncClient() {
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient, ClientRole.PUBLISHER);

            underTest.publish(GENERIC_MQTT_PUBLISH);

            Mockito.verify(mqtt5AsyncClient).publish(Mockito.eq(MQTT_5_PUBLISH));
        }

        @Test
        public void sendPublishReturnsCompletedStageWithExpectedSuccessResultIfNoErrorOccurred() {
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient, ClientRole.PUBLISHER);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.success(GENERIC_MQTT_PUBLISH));
        }

        @Test
        public void sendPublishReturnsFailedStageWithExpectedErrorIfErrorOccurredInBand() {
            final var error = new IllegalStateException("This is totally expected.");
            Mockito.when(mqtt5PublishResult.getError()).thenReturn(Optional.of(error));
            Mockito.when(mqtt5AsyncClient.publish(Mockito.eq(MQTT_5_PUBLISH)))
                    .thenReturn(CompletableFuture.completedFuture(mqtt5PublishResult));
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient, ClientRole.PUBLISHER);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, error));
        }

        @Test
        public void sendPublishReturnsFailedStageWithExpectedErrorIfErrorOccurredOutOfBand() {
            final var error = new IllegalStateException("This is totally expected.");
            Mockito.when(mqtt5AsyncClient.publish(Mockito.eq(MQTT_5_PUBLISH)))
                    .thenReturn(CompletableFuture.failedFuture(error));
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient, ClientRole.PUBLISHER);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, error));
        }

        @Test
        public void toStringReturnsExpected() {
            final var mqttClientId = "Brown";
            final var mqtt5ClientConfig = Mockito.mock(Mqtt5ClientConfig.class);
            Mockito.when(mqtt5ClientConfig.getClientIdentifier())
                    .thenReturn(Optional.of(MqttClientIdentifier.of(mqttClientId)));
            Mockito.when(mqtt5AsyncClient.getConfig()).thenReturn(mqtt5ClientConfig);
            final var clientRole = ClientRole.PUBLISHER;
            final var underTest =
                    BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient, clientRole);

            assertThat(underTest).hasToString(clientRole + ":" + mqttClientId);
        }

    }

}