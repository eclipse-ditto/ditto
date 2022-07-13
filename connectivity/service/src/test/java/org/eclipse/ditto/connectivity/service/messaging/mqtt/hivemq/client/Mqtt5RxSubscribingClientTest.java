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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscription;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.exceptions.MqttSessionExpiredException;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5RxClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Unit test for {@link BaseGenericMqttSubscribingClient} for MQTT protocol version 5.
 */
@RunWith(MockitoJUnitRunner.class)
public final class Mqtt5RxSubscribingClientTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

    private static ActorSystem actorSystem;

    @Rule public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock private Mqtt5RxClient mqtt5RxClient;
    @Mock private Mqtt5AsyncClient mqtt5AsyncClient;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
    }

    @Before
    public void before() {
        Mockito.when(mqtt5RxClient.toAsync()).thenReturn(mqtt5AsyncClient);
    }

    @Test
    public void ofMqtt5RxClientWithNullClientThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> BaseGenericMqttSubscribingClient.ofMqtt5RxClient(null, ClientRole.CONSUMER))
                .withMessage("The mqtt5RxClient must not be null!")
                .withNoCause();
    }

    @Test
    public void ofMqtt5RxClientWithNullClientRoleThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, null))
                .withMessage("The clientRole must not be null!")
                .withNoCause();
    }

    @Test
    public void subscribeWithNullGenericMqttSubscribeThrowsException() {
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, ClientRole.CONSUMER);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.subscribe(null))
                .withMessage("The genericMqttSubscribe must not be null!")
                .withNoCause();
    }

    @Test
    public void subscribeCallsHiveMqtt5RxClient() {
        final var mqtt5SubscribeArgumentCaptor = ArgumentCaptor.forClass(Mqtt5Subscribe.class);
        Mockito.when(mqtt5RxClient.subscribe(mqtt5SubscribeArgumentCaptor.capture())).thenReturn(Single.never());
        final var topicFiltersAndQos = new LinkedHashMap<MqttTopicFilter, MqttQos>();
        topicFiltersAndQos.put(MqttTopicFilter.of("source/foo"), MqttQos.AT_LEAST_ONCE);
        topicFiltersAndQos.put(MqttTopicFilter.of("source/bar"), MqttQos.AT_MOST_ONCE);
        topicFiltersAndQos.put(MqttTopicFilter.of("source/baz"), MqttQos.EXACTLY_ONCE);
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, ClientRole.CONSUMER);

        underTest.subscribe(
                GenericMqttSubscribe.of(topicFiltersAndQos.entrySet()
                        .stream()
                        .map(entry -> GenericMqttSubscription.newInstance(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
        );

        softly.assertThat(mqtt5SubscribeArgumentCaptor.getValue())
                .isEqualTo(Mqtt5Subscribe.builder()
                        .addSubscriptions(topicFiltersAndQos.entrySet()
                                .stream()
                                .map(entry -> Mqtt5Subscription.builder()
                                        .topicFilter(entry.getKey())
                                        .qos(entry.getValue())
                                        .build()))
                        .build());
    }

    @Test
    public void subscribeWhenSubAckHasOnlySuccessReturnCodeSucceeds() {
        final var mqtt5SubAck = Mockito.mock(Mqtt5SubAck.class);
        Mockito.when(mqtt5SubAck.getReasonCodes()).thenReturn(List.of(Mqtt5SubAckReasonCode.GRANTED_QOS_1));
        Mockito.when(mqtt5RxClient.subscribe(Mockito.any())).thenReturn(Single.just(mqtt5SubAck));
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, ClientRole.CONSUMER);

        final var genericMqttSubAckSingle = underTest.subscribe(
                GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/status"),
                        MqttQos.AT_LEAST_ONCE)))
        );

        softly.assertThat(subscribeForSingleValueResponse(genericMqttSubAckSingle))
                .succeedsWithin(Duration.ofMillis(500L))
                .isEqualTo(GenericMqttSubAck.ofMqtt5SubAck(mqtt5SubAck));
    }

    private static <T> CompletableFuture<T> subscribeForSingleValueResponse(final Single<T> singleValueResponse) {
        final var result = new CompletableFuture<T>();
        singleValueResponse.subscribe(result::complete, result::completeExceptionally);
        return result;
    }

    @Test
    public void subscribeWhenSubAckHasOnlyErrorReasonCodeErrors() {
        final var mqtt5SubAck = Mockito.mock(Mqtt5SubAck.class);
        final var mqtt5SubAckReasonCode = Mqtt5SubAckReasonCode.NOT_AUTHORIZED;
        Mockito.when(mqtt5SubAck.getReasonCodes()).thenReturn(List.of(mqtt5SubAckReasonCode));
        final var mqtt5SubAckException = new Mqtt5SubAckException(mqtt5SubAck, null);
        Mockito.when(mqtt5RxClient.subscribe(Mockito.any())).thenReturn(Single.error(mqtt5SubAckException));
        final var mqttTopicFilter = MqttTopicFilter.of("source/status");
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, ClientRole.CONSUMER);

        final var genericMqttSubAckSingle = underTest.subscribe(
                GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(mqttTopicFilter,
                        MqttQos.AT_LEAST_ONCE)))
        );

        softly.assertThat(subscribeForSingleValueResponse(genericMqttSubAckSingle))
                .failsWithin(Duration.ofMillis(500L))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOfSatisfying(AllSubscriptionsFailedException.class,
                        exception -> softly.assertThat(exception.failedSubscriptionStatuses())
                                .containsOnly(SubscriptionStatus.newInstance(
                                        mqttTopicFilter,
                                        GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(mqtt5SubAckReasonCode)
                                )))
                .withCause(mqtt5SubAckException);
    }

    @Test
    public void subscribeWhenSubAckHasSomeErrorReasonCodeErrors() {
        final var topicFiltersAndReasonCodes = new LinkedHashMap<MqttTopicFilter, Mqtt5SubAckReasonCode>();
        topicFiltersAndReasonCodes.put(MqttTopicFilter.of("source/foo"), Mqtt5SubAckReasonCode.GRANTED_QOS_1);
        topicFiltersAndReasonCodes.put(MqttTopicFilter.of("source/bar"), Mqtt5SubAckReasonCode.NOT_AUTHORIZED);
        topicFiltersAndReasonCodes.put(MqttTopicFilter.of("source/baz"), Mqtt5SubAckReasonCode.GRANTED_QOS_0);
        topicFiltersAndReasonCodes.put(MqttTopicFilter.of("source/yolo"), Mqtt5SubAckReasonCode.QUOTA_EXCEEDED);

        final var mqtt5SubAck = Mockito.mock(Mqtt5SubAck.class);
        Mockito.when(mqtt5SubAck.getReasonCodes()).thenReturn(List.copyOf(topicFiltersAndReasonCodes.values()));
        Mockito.when(mqtt5RxClient.subscribe(Mockito.any())).thenReturn(Single.just(mqtt5SubAck));
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, ClientRole.CONSUMER);

        final var genericMqttSubAckSingle = underTest.subscribe(
                GenericMqttSubscribe.of(topicFiltersAndReasonCodes.keySet()
                        .stream()
                        .map(topicFilter -> GenericMqttSubscription.newInstance(topicFilter, MqttQos.AT_LEAST_ONCE))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
        );

        softly.assertThat(subscribeForSingleValueResponse(genericMqttSubAckSingle))
                .failsWithin(Duration.ofMillis(500L))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOfSatisfying(SomeSubscriptionsFailedException.class,
                        exception -> softly.assertThat(exception.failedSubscriptionStatuses())
                                .hasSameElementsAs(topicFiltersAndReasonCodes.entrySet()
                                        .stream()
                                        .filter(entry -> entry.getValue().isError())
                                        .map(entry -> SubscriptionStatus.newInstance(
                                                entry.getKey(),
                                                GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(entry.getValue())
                                        ))
                                        .toList()))
                .withNoCause();
    }

    @Test
    public void subscribeWhenExceptionOccursBeforeSubscribeMessageWasSent() {
        final var illegalStateException = new IllegalStateException("Yolo!");
        Mockito.when(mqtt5RxClient.subscribe(Mockito.any())).thenReturn(Single.error(illegalStateException));
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, ClientRole.CONSUMER);

        final var genericMqttSubAckSingle = underTest.subscribe(
                GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/status"),
                        MqttQos.AT_LEAST_ONCE)))
        );

        softly.assertThat(subscribeForSingleValueResponse(genericMqttSubAckSingle))
                .failsWithin(Duration.ofMillis(500L))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOfSatisfying(MqttSubscribeException.class,
                        mqttSubscribeException -> {
                            softly.assertThat(mqttSubscribeException).hasMessage(illegalStateException.getMessage());
                            softly.assertThat(mqttSubscribeException).hasCause(illegalStateException);
                        });
    }

    @Test
    public void consumeSubscribedPublishesWithManualAcknowledgementEmitsExpected() {
        final var mqttTopicFilter = MqttTopicFilter.of("source/status");
        final var mqttQos = MqttQos.AT_LEAST_ONCE;
        final var mqtt5Publish = Mqtt5Publish.builder()
                .topic(MqttTopic.of(mqttTopicFilter.toString()))
                .qos(mqttQos)
                .payload(ByteBufferUtils.fromUtf8String("online"))
                .build();
        Mockito.when(mqtt5RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.SUBSCRIBED), Mockito.eq(true)))
                .thenReturn(Flowable.just(mqtt5Publish));
        final var onCompleteMessage = "done";
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, ClientRole.CONSUMER);

        final var genericMqttPublishFlowable = underTest.consumeSubscribedPublishesWithManualAcknowledgement();

        final var mqttPublishSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
        Source.fromPublisher(genericMqttPublishFlowable)
                .to(Sink.actorRef(mqttPublishSourceTestKit.getRef(), onCompleteMessage))
                .run(actorSystem);

        final var genericMqttPublish = mqttPublishSourceTestKit.expectMsgClass(GenericMqttPublish.class);
        mqttPublishSourceTestKit.expectMsg(onCompleteMessage);

        assertThat(genericMqttPublish).isEqualTo(GenericMqttPublish.ofMqtt5Publish(mqtt5Publish));
    }

    @Test
    public void consumeSubscribedPublishesWithManualAcknowledgementErrorsIfSessionExpired() {
        final var mqttSessionExpiredException = new MqttSessionExpiredException("Your session expired.", null);
        Mockito.when(mqtt5RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.SUBSCRIBED), Mockito.eq(true)))
                .thenReturn(Flowable.error(mqttSessionExpiredException));
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient, ClientRole.CONSUMER);

        final var genericMqttPublishFlowable = underTest.consumeSubscribedPublishesWithManualAcknowledgement();

        final var mqttPublishSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
        Source.fromPublisher(genericMqttPublishFlowable)
                .to(Sink.actorRef(mqttPublishSourceTestKit.getRef(), "done"))
                .run(actorSystem);

        final var failure = mqttPublishSourceTestKit.expectMsgClass(Status.Failure.class);
        mqttPublishSourceTestKit.expectNoMessage();

        assertThat(failure.cause()).isEqualTo(mqttSessionExpiredException);
    }

}
