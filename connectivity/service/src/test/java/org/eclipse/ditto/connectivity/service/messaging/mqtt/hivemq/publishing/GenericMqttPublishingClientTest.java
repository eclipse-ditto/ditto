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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publish.GenericMqttPublish;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;

/**
 * Unit test for {@link GenericMqttPublishingClient}.
 */
@RunWith(Enclosed.class)
public final class GenericMqttPublishingClientTest {

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
        public void ofMqtt3AsyncClientWithNullThrowsException() {
            Assertions.assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttPublishingClient.ofMqtt3AsyncClient(null))
                    .withMessage("The mqtt3AsyncClient must not be null!")
                    .withNoCause();
        }

        @Test
        public void publishWithNullThrowsException() {
            final var underTest = GenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient);

            Assertions.assertThatNullPointerException()
                    .isThrownBy(() -> underTest.publish(null))
                    .withMessage("The genericMqttPublish must not be null!")
                    .withNoCause();
        }

        @Test
        public void publishIsDelegatedToMqtt3AsyncClient() {
            final var underTest = GenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient);

            underTest.publish(GENERIC_MQTT_PUBLISH);

            Mockito.verify(mqtt3AsyncClient).publish(Mockito.eq(MQTT_3_PUBLISH));
        }

        @Test
        public void sendPublishReturnsCompletedStageWithExpectedSuccessResultIfNoErrorOccurred() {
            Mockito.when(mqtt3AsyncClient.publish(Mockito.eq(MQTT_3_PUBLISH)))
                    .thenReturn(CompletableFuture.completedFuture(MQTT_3_PUBLISH));
            final var underTest = GenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.success(GENERIC_MQTT_PUBLISH));
        }

        @Test
        public void sendPublishReturnsFailedStageWithExpectedErrorIfErrorOccurred() {
            final var error = new IllegalStateException("This is totally expected.");
            Mockito.when(mqtt3AsyncClient.publish(Mockito.eq(MQTT_3_PUBLISH)))
                    .thenReturn(CompletableFuture.failedFuture(error));
            final var underTest = GenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3AsyncClient);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, error));
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
        public void ofMqtt5AsyncClientWithNullThrowsException() {
            Assertions.assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttPublishingClient.ofMqtt5AsyncClient(null))
                    .withMessage("The mqtt5AsyncClient must not be null!")
                    .withNoCause();
        }

        @Test
        public void publishWithNullThrowsException() {
            final var underTest = GenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient);

            Assertions.assertThatNullPointerException()
                    .isThrownBy(() -> underTest.publish(null))
                    .withMessage("The genericMqttPublish must not be null!")
                    .withNoCause();
        }

        @Test
        public void publishIsDelegatedToMqtt5AsyncClient() {
            final var underTest = GenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient);

            underTest.publish(GENERIC_MQTT_PUBLISH);

            Mockito.verify(mqtt5AsyncClient).publish(Mockito.eq(MQTT_5_PUBLISH));
        }

        @Test
        public void sendPublishReturnsCompletedStageWithExpectedSuccessResultIfNoErrorOccurred() {
            final var underTest = GenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient);

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
            final var underTest = GenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, error));
        }

        @Test
        public void sendPublishReturnsFailedStageWithExpectedErrorIfErrorOccurredOutOfBand() {
            final var error = new IllegalStateException("This is totally expected.");
            Mockito.when(mqtt5AsyncClient.publish(Mockito.eq(MQTT_5_PUBLISH)))
                    .thenReturn(CompletableFuture.failedFuture(error));
            final var underTest = GenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5AsyncClient);

            final var publishResultCompletionStage = underTest.publish(GENERIC_MQTT_PUBLISH);

            assertThat(publishResultCompletionStage)
                    .isCompletedWithValue(GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, error));
        }

    }

}