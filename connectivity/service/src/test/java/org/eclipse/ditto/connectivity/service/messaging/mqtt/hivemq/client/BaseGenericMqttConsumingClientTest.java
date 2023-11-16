/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientConfig;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientConfig;
import com.hivemq.client.mqtt.mqtt5.Mqtt5RxClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.PublishSubject;

/**
 * Unit test for {@link BaseGenericMqttConsumingClient}.
 */
@RunWith(Enclosed.class)
public final class BaseGenericMqttConsumingClientTest {

    @RunWith(MockitoJUnitRunner.class)
    public static final class Mqtt3RxClientTest {

        private static final Mqtt3Publish MQTT_3_PUBLISH = Mockito.mock(Mqtt3Publish.class);
        private static final GenericMqttPublish GENERIC_MQTT_PUBLISH = GenericMqttPublish.ofMqtt3Publish(MQTT_3_PUBLISH);

        @Mock
        private Mqtt3RxClient mqtt3RxClient;

        @Before
        public void before() {
            Mockito.when(mqtt3RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(Flowable.never());
        }

        @Test
        public void ofMqtt3RxClientWithNullMqtt3RxClientThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> BaseGenericMqttConsumingClient.ofMqtt3RxClient(null))
                    .withMessage("The mqtt3RxClient must not be null!")
                    .withNoCause();
        }

        @Test
        public void consumePublishesIsDelegatedToMqtt3RxClient() {
            Mockito.when(mqtt3RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(Flowable.just(MQTT_3_PUBLISH));
            final var underTest = BaseGenericMqttConsumingClient.ofMqtt3RxClient(mqtt3RxClient);

            final var publishes = new ArrayList<GenericMqttPublish>();
            underTest.consumePublishes().blockingSubscribe(publishes::add);

            assertThat(publishes).containsExactly(GENERIC_MQTT_PUBLISH);
        }

        @Test
        public void consumePublishesEmitsError() {
            final var error = new Exception();
            Mockito.when(mqtt3RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(Flowable.error(error));
            final var underTest = BaseGenericMqttConsumingClient.ofMqtt3RxClient(mqtt3RxClient);

            final var errors = new ArrayList<Throwable>();
            underTest.consumePublishes().blockingSubscribe(
                    ignored -> {},
                    errors::add);

            assertThat(errors).containsExactly(error);
        }

        @Test
        public void consumePublishesEmitsCompletion() {
            Mockito.when(mqtt3RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(Flowable.empty());
            final var underTest = BaseGenericMqttConsumingClient.ofMqtt3RxClient(mqtt3RxClient);

            final var completed = new ArrayList<Boolean>();
            underTest.consumePublishes().blockingSubscribe(
                    ignored -> {},
                    ignored -> {},
                    () -> completed.add(true));

            assertThat(completed).containsExactly(true);
        }

        @Test
        public void consumePublishesEmitsOnlyNewItemsWhenBufferingIsStopped() {
            final var subject = PublishSubject.<Mqtt3Publish>create();
            Mockito.when(mqtt3RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(subject.toFlowable(BackpressureStrategy.DROP));
            final var underTest = BaseGenericMqttConsumingClient.ofMqtt3RxClient(mqtt3RxClient);

            final var newMqtt3Publish = Mockito.mock(Mqtt3Publish.class);
            final var newGenericMqttPublish = GenericMqttPublish.ofMqtt3Publish(newMqtt3Publish);

            final var publishes = new ArrayList<GenericMqttPublish>();
            subject.onNext(MQTT_3_PUBLISH);
            underTest.stopBufferingPublishes();
            final var subscription = underTest.consumePublishes().subscribe(publishes::add);
            subject.onNext(newMqtt3Publish);
            subject.onComplete();
            subscription.dispose();

            assertThat(publishes).containsExactly(newGenericMqttPublish);
        }

        @Test
        public void toStringReturnsExpected() {
            final var mqttClientId = "Anneliese";
            final var mqtt3ClientConfig = Mockito.mock(Mqtt3ClientConfig.class);
            Mockito.when(mqtt3ClientConfig.getClientIdentifier())
                    .thenReturn(Optional.of(MqttClientIdentifier.of(mqttClientId)));
            Mockito.when(mqtt3RxClient.getConfig()).thenReturn(mqtt3ClientConfig);
            final var underTest =
                    BaseGenericMqttConsumingClient.ofMqtt3RxClient(mqtt3RxClient);

            assertThat(underTest).hasToString(mqttClientId);
        }

    }

    @RunWith(MockitoJUnitRunner.class)
    public static final class Mqtt5RxClientTest {

        private static final Mqtt5Publish MQTT_5_PUBLISH = Mockito.mock(Mqtt5Publish.class);
        private static final GenericMqttPublish GENERIC_MQTT_PUBLISH = GenericMqttPublish.ofMqtt5Publish(MQTT_5_PUBLISH);

        @Mock
        private Mqtt5RxClient mqtt5RxClient;

        @Before
        public void before() {
            Mockito.when(mqtt5RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(Flowable.never());
        }

        @Test
        public void ofMqtt5RxClientWithNullMqtt5RxClientThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> BaseGenericMqttConsumingClient.ofMqtt5RxClient(null))
                    .withMessage("The mqtt5RxClient must not be null!")
                    .withNoCause();
        }

        @Test
        public void consumePublishesIsDelegatedToMqtt5RxClient() {
            Mockito.when(mqtt5RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(Flowable.just(MQTT_5_PUBLISH));
            final var underTest = BaseGenericMqttConsumingClient.ofMqtt5RxClient(mqtt5RxClient);

            final var publishes = new ArrayList<GenericMqttPublish>();
            underTest.consumePublishes().blockingSubscribe(publishes::add);

            assertThat(publishes).containsExactly(GENERIC_MQTT_PUBLISH);
        }

        @Test
        public void consumePublishesEmitsError() {
            final var error = new Exception();
            Mockito.when(mqtt5RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(Flowable.error(error));
            final var underTest = BaseGenericMqttConsumingClient.ofMqtt5RxClient(mqtt5RxClient);

            final var errors = new ArrayList<Throwable>();
            underTest.consumePublishes().blockingSubscribe(
                    ignored -> {},
                    errors::add);

            assertThat(errors).containsExactly(error);
        }

        @Test
        public void consumePublishesEmitsCompletion() {
            Mockito.when(mqtt5RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(Flowable.empty());
            final var underTest = BaseGenericMqttConsumingClient.ofMqtt5RxClient(mqtt5RxClient);

            final var completed = new ArrayList<Boolean>();
            underTest.consumePublishes().blockingSubscribe(
                    ignored -> {},
                    ignored -> {},
                    () -> completed.add(true));

            assertThat(completed).containsExactly(true);
        }

        @Test
        public void consumePublishesEmitsOnlyNewItemsWhenBufferingIsStopped() {
            final var subject = PublishSubject.<Mqtt5Publish>create();
            Mockito.when(mqtt5RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.ALL), Mockito.eq(true)))
                    .thenReturn(subject.toFlowable(BackpressureStrategy.DROP));
            final var underTest = BaseGenericMqttConsumingClient.ofMqtt5RxClient(mqtt5RxClient);

            final var newMqtt5Publish = Mockito.mock(Mqtt5Publish.class);
            final var newGenericMqttPublish = GenericMqttPublish.ofMqtt5Publish(newMqtt5Publish);

            final var publishes = new ArrayList<GenericMqttPublish>();
            subject.onNext(MQTT_5_PUBLISH);
            underTest.stopBufferingPublishes();
            final var subscription = underTest.consumePublishes().subscribe(publishes::add);
            subject.onNext(newMqtt5Publish);
            subject.onComplete();
            subscription.dispose();

            assertThat(publishes).containsExactly(newGenericMqttPublish);
        }

        @Test
        public void toStringReturnsExpected() {
            final var mqttClientId = "Anneliese";
            final var mqtt5ClientConfig = Mockito.mock(Mqtt5ClientConfig.class);
            Mockito.when(mqtt5ClientConfig.getClientIdentifier())
                    .thenReturn(Optional.of(MqttClientIdentifier.of(mqttClientId)));
            Mockito.when(mqtt5RxClient.getConfig()).thenReturn(mqtt5ClientConfig);
            final var underTest =
                    BaseGenericMqttConsumingClient.ofMqtt5RxClient(mqtt5RxClient);

            assertThat(underTest).hasToString(mqttClientId);
        }

    }

}