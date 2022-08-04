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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import static com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode.GRANTED_QOS_1;
import static com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode.QUOTA_EXCEEDED;
import static com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode.UNSPECIFIED_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SomeSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SubscriptionStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

import akka.actor.ActorSystem;
import akka.stream.javadsl.Sink;
import akka.stream.testkit.javadsl.TestSink;
import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Unit test for {@link MqttSubscriber}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MqttSubscriberTest {

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock
    private GenericMqttClient genericMqttClient;

    private ActorSystem actorSystem;

    @Before
    public void before() {
        actorSystem = actorSystemResource.getActorSystem();
    }


    @Test
    public void newInstanceWithNullClientThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttSubscriber.newInstance(null))
                .withMessage("The genericMqttSubscribingClient must not be null!")
                .withNoCause();
    }

    @Test
    public void subscribeForConnectionSourceWithNullConnectionSourceThrowsException() {
        final var underTest = MqttSubscriber.newInstance(genericMqttClient);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.subscribeForConnectionSources(null))
                .withMessage("The connectionSources must not be null!")
                .withNoCause();
    }

    @Test
    public void subscribeForConnectionSourceWithEmptyCollectionReturnsEmptySource() {
        final var underTest = MqttSubscriber.newInstance(genericMqttClient);

        final var subscribeResultSource =
                underTest.subscribeForConnectionSources(Collections.emptyList());

        subscribeResultSource.runWith(TestSink.probe(actorSystem), actorSystem).expectSubscriptionAndComplete();
    }

    @Test
    public void subscribeForConnectionSourcesWithInvalidSourceAddressReturnsSourceWithSubscribeFailure() {
        final var underTest = MqttSubscriber.newInstance(genericMqttClient);

        final var subscribeResultSource = underTest.subscribeForConnectionSources(
                List.of(mockConnectionSource(Set.of("#/#"), MqttQos.EXACTLY_ONCE))
        );

        final var testKit = actorSystemResource.newTestKit();
        final var onCompleteMessage = "done";
        subscribeResultSource.to(Sink.actorRef(testKit.getRef(), onCompleteMessage)).run(actorSystem);

        assertThat(testKit.expectMsgClass(SubscribeResult.class))
                .satisfies(subscribeFailure -> assertThat(subscribeFailure.getErrorOrThrow())
                        .isInstanceOf(MqttSubscribeException.class)
                        .hasMessageStartingWith("Failed to instantiate GenericMqttSubscribe: ")
                        .hasCauseInstanceOf(InvalidMqttTopicFilterStringException.class));
        testKit.expectMsg(onCompleteMessage);
    }

    @Test
    public void subscribeForConnectionSourcesWhenAllSubscriptionsSuccessfulReturnsExpectedSource() {
        final var genericMqttSubAck = Mockito.mock(GenericMqttSubAck.class);
        Mockito.when(genericMqttClient.subscribe(Mockito.any(GenericMqttSubscribe.class)))
                .thenReturn(Single.just(genericMqttSubAck));
        Mockito.when(genericMqttClient.consumeSubscribedPublishesWithManualAcknowledgement())
                .thenReturn(Flowable.never());
        final var mqttQos = MqttQos.AT_LEAST_ONCE;
        final var connectionSource1 = mockConnectionSource(Set.of("foo", "bar"), mqttQos);
        final var connectionSource2 = mockConnectionSource(Set.of("baz"), mqttQos);
        final var underTest = MqttSubscriber.newInstance(genericMqttClient);

        final var subscribeResultSource =
                underTest.subscribeForConnectionSources(List.of(connectionSource1, connectionSource2));

        final var testKit = actorSystemResource.newTestKit();
        final var onCompleteMessage = "complete";
        subscribeResultSource.to(Sink.actorRef(testKit.getRef(), onCompleteMessage)).run(actorSystem);

        softly.assertThat(testKit.expectMsgClass(SubscribeResult.class))
                .as("first subscribe result")
                .satisfies(subscribeResult -> {
                    softly.assertThat(subscribeResult.getConnectionSource())
                            .as("first connection source")
                            .isEqualTo(connectionSource1);
                    softly.assertThat(subscribeResult.isSuccess()).as("is success").isTrue();
                });
        softly.assertThat(testKit.expectMsgClass(SubscribeResult.class))
                .as("second subscribe result")
                .satisfies(subscribeResult -> {
                    softly.assertThat(subscribeResult.getConnectionSource())
                            .as("second connection source")
                            .isEqualTo(connectionSource2);
                    softly.assertThat(subscribeResult.isSuccess()).as("is success").isTrue();
                });
        testKit.expectMsg(onCompleteMessage);
    }

    private static Source mockConnectionSource(final Set<String> sourceAddresses, final MqttQos mqttQos) {
        final var result = Mockito.mock(Source.class);
        Mockito.when(result.getAddresses()).thenReturn(sourceAddresses);
        Mockito.when(result.getQos()).thenReturn(Optional.of(mqttQos.getCode()));
        return result;
    }

    @Test
    public void subscribeForConnectionSourcesWhenSomeSubscriptionsFailedReturnsExpectedSource() {
        final var topicSubAckStatuses = new LinkedHashMap<String, GenericMqttSubAckStatus>();
        topicSubAckStatuses.put("foo", GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(GRANTED_QOS_1));
        topicSubAckStatuses.put("bar", GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(UNSPECIFIED_ERROR));
        topicSubAckStatuses.put("baz", GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(GRANTED_QOS_1));
        topicSubAckStatuses.put("yolo", GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(QUOTA_EXCEEDED));

        final var someSubscriptionsFailedException = new SomeSubscriptionsFailedException(topicSubAckStatuses.entrySet()
                .stream()
                .filter(entry -> entry.getValue().isError())
                .map(entry -> SubscriptionStatus.newInstance(MqttTopicFilter.of(entry.getKey()), entry.getValue()))
                .toList());
        Mockito.when(genericMqttClient.subscribe(Mockito.any(GenericMqttSubscribe.class)))
                .thenReturn(Single.error(someSubscriptionsFailedException));
        final var connectionSource = mockConnectionSource(topicSubAckStatuses.keySet(), MqttQos.AT_LEAST_ONCE);
        final var underTest = MqttSubscriber.newInstance(genericMqttClient);

        final var subscribeResultSource = underTest.subscribeForConnectionSources(List.of(connectionSource));

        final var testKit = actorSystemResource.newTestKit();
        final var onCompleteMessage = "complete";
        subscribeResultSource.to(Sink.actorRef(testKit.getRef(), onCompleteMessage)).run(actorSystem);

        softly.assertThat(testKit.expectMsgClass(SubscribeResult.class))
                .as("subscribe result")
                .satisfies(subscribeResult -> {
                    softly.assertThat(subscribeResult.getConnectionSource())
                            .as("connection source")
                            .isEqualTo(connectionSource);
                    softly.assertThat(subscribeResult.isFailure()).as("is failure").isTrue();
                    softly.assertThat(subscribeResult.getErrorOrThrow())
                            .as("error")
                            .isEqualTo(someSubscriptionsFailedException);
                });
        testKit.expectMsg(onCompleteMessage);
    }

}