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
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3SubAckException;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;

import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Unit test for {@link BaseGenericMqttSubscribingClient} for MQTT protocol version 3.
 */
@RunWith(MockitoJUnitRunner.class)
public final class Mqtt3RxSubscribingClientTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

    private static ActorSystem actorSystem;

    @Rule public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock private Mqtt3RxClient mqtt3RxClient;
    @Mock private Mqtt3AsyncClient mqtt3AsyncClient;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
    }

    @Before
    public void before() {
        Mockito.when(mqtt3RxClient.toAsync()).thenReturn(mqtt3AsyncClient);
    }

    @Test
    public void ofMqtt3RxClientWithNullClientThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> BaseGenericMqttSubscribingClient.ofMqtt3RxClient(null, ClientRole.CONSUMER))
                .withMessage("The mqtt3RxClient must not be null!")
                .withNoCause();
    }

    @Test
    public void ofMqtt3RxClientWithNullClientRoleThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, null))
                .withMessage("The clientRole must not be null!")
                .withNoCause();
    }

    @Test
    public void subscribeWithNullGenericMqttSubscribeThrowsException() {
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, ClientRole.CONSUMER);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.subscribe(null))
                .withMessage("The genericMqttSubscribe must not be null!")
                .withNoCause();
    }

    @Test
    public void subscribeCallsHiveMqtt3RxClient() {
        final var mqtt3SubscribeArgumentCaptor = ArgumentCaptor.forClass(Mqtt3Subscribe.class);
        Mockito.when(mqtt3RxClient.subscribe(mqtt3SubscribeArgumentCaptor.capture())).thenReturn(Single.never());
        final var topicFiltersAndQos = new LinkedHashMap<MqttTopicFilter, MqttQos>();
        topicFiltersAndQos.put(MqttTopicFilter.of("source/foo"), MqttQos.AT_LEAST_ONCE);
        topicFiltersAndQos.put(MqttTopicFilter.of("source/bar"), MqttQos.AT_MOST_ONCE);
        topicFiltersAndQos.put(MqttTopicFilter.of("source/baz"), MqttQos.EXACTLY_ONCE);
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, ClientRole.CONSUMER);

        underTest.subscribe(
                GenericMqttSubscribe.of(topicFiltersAndQos.entrySet()
                        .stream()
                        .map(entry -> GenericMqttSubscription.newInstance(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
        );

        softly.assertThat(mqtt3SubscribeArgumentCaptor.getValue())
                .isEqualTo(Mqtt3Subscribe.builder()
                        .addSubscriptions(topicFiltersAndQos.entrySet()
                                .stream()
                                .map(entry -> Mqtt3Subscription.builder()
                                        .topicFilter(entry.getKey())
                                        .qos(entry.getValue())
                                        .build()))
                        .build());
    }

    @Test
    public void subscribeWhenSubAckHasOnlySuccessReturnCodeSucceeds() {
        final var mqtt3SubAck = Mockito.mock(Mqtt3SubAck.class);
        Mockito.when(mqtt3SubAck.getReturnCodes()).thenReturn(List.of(Mqtt3SubAckReturnCode.SUCCESS_MAXIMUM_QOS_1));
        Mockito.when(mqtt3RxClient.subscribe(Mockito.any())).thenReturn(Single.just(mqtt3SubAck));
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, ClientRole.CONSUMER);

        final var genericMqttSubAckSingle = underTest.subscribe(
                GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/status"),
                        MqttQos.AT_LEAST_ONCE)))
        );

        softly.assertThat(subscribeForSingleValueResponse(genericMqttSubAckSingle))
                .succeedsWithin(Duration.ofMillis(500L))
                .isEqualTo(GenericMqttSubAck.ofMqtt3SubAck(mqtt3SubAck));
    }

    private static <T> CompletableFuture<T> subscribeForSingleValueResponse(final Single<T> singleValueResponse) {
        final var result = new CompletableFuture<T>();
        singleValueResponse.subscribe(result::complete, result::completeExceptionally);
        return result;
    }

    @Test
    public void subscribeWhenSubAckHasOnlyErrorReturnCodeErrors() {
        final var mqtt3SubAck = Mockito.mock(Mqtt3SubAck.class);
        final var mqtt3SubAckReturnCode = Mqtt3SubAckReturnCode.FAILURE;
        Mockito.when(mqtt3SubAck.getReturnCodes()).thenReturn(List.of(mqtt3SubAckReturnCode));
        final var mqtt3SubAckException = new Mqtt3SubAckException(mqtt3SubAck, null, null);
        Mockito.when(mqtt3RxClient.subscribe(Mockito.any())).thenReturn(Single.error(mqtt3SubAckException));
        final var mqttTopicFilter = MqttTopicFilter.of("source/status");
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, ClientRole.CONSUMER);

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
                                        GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(mqtt3SubAckReturnCode)
                                )))
                .withCause(mqtt3SubAckException);
    }

    @Test
    public void subscribeWhenSubAckHasSomeErrorReturnCodeErrors() {
        final var topicFiltersAndReturnCodes = new LinkedHashMap<MqttTopicFilter, Mqtt3SubAckReturnCode>();
        topicFiltersAndReturnCodes.put(MqttTopicFilter.of("source/foo"), Mqtt3SubAckReturnCode.SUCCESS_MAXIMUM_QOS_1);
        topicFiltersAndReturnCodes.put(MqttTopicFilter.of("source/bar"), Mqtt3SubAckReturnCode.FAILURE);
        topicFiltersAndReturnCodes.put(MqttTopicFilter.of("source/baz"), Mqtt3SubAckReturnCode.SUCCESS_MAXIMUM_QOS_0);
        topicFiltersAndReturnCodes.put(MqttTopicFilter.of("source/yolo"), Mqtt3SubAckReturnCode.FAILURE);

        final var mqtt3SubAck = Mockito.mock(Mqtt3SubAck.class);
        Mockito.when(mqtt3SubAck.getReturnCodes()).thenReturn(List.copyOf(topicFiltersAndReturnCodes.values()));
        Mockito.when(mqtt3RxClient.subscribe(Mockito.any())).thenReturn(Single.just(mqtt3SubAck));
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, ClientRole.CONSUMER);

        final var genericMqttSubAckSingle = underTest.subscribe(
                GenericMqttSubscribe.of(topicFiltersAndReturnCodes.keySet()
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
                                .hasSameElementsAs(topicFiltersAndReturnCodes.entrySet()
                                        .stream()
                                        .filter(entry -> entry.getValue().isError())
                                        .map(entry -> SubscriptionStatus.newInstance(
                                                entry.getKey(),
                                                GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(entry.getValue())
                                        ))
                                        .toList()))
                .withNoCause();
    }

    @Test
    public void subscribeWhenExceptionOccursBeforeSubscribeMessageWasSent() {
        final var illegalStateException = new IllegalStateException("Yolo!");
        Mockito.when(mqtt3RxClient.subscribe(Mockito.any())).thenReturn(Single.error(illegalStateException));
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, ClientRole.CONSUMER);

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
        final var mqtt3Publish = Mqtt3Publish.builder()
                .topic(MqttTopic.of(mqttTopicFilter.toString()))
                .qos(mqttQos)
                .payload(ByteBufferUtils.fromUtf8String("online"))
                .build();
        Mockito.when(mqtt3RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.SUBSCRIBED), Mockito.eq(true)))
                .thenReturn(Flowable.just(mqtt3Publish));
        final var onCompleteMessage = "done";
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, ClientRole.CONSUMER);

        final var genericMqttPublishFlowable = underTest.consumeSubscribedPublishesWithManualAcknowledgement();

        final var mqttPublishSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
        Source.fromPublisher(genericMqttPublishFlowable)
                .to(Sink.actorRef(mqttPublishSourceTestKit.getRef(), onCompleteMessage))
                .run(actorSystem);

        final var genericMqttPublish = mqttPublishSourceTestKit.expectMsgClass(GenericMqttPublish.class);
        mqttPublishSourceTestKit.expectMsg(onCompleteMessage);

        assertThat(genericMqttPublish).isEqualTo(GenericMqttPublish.ofMqtt3Publish(mqtt3Publish));
    }

    @Test
    public void consumeSubscribedPublishesWithManualAcknowledgementErrorsIfSessionExpired() {
        final var mqttSessionExpiredException = new MqttSessionExpiredException("Your session expired.", null);
        Mockito.when(mqtt3RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.SUBSCRIBED), Mockito.eq(true)))
                .thenReturn(Flowable.error(mqttSessionExpiredException));
        final var underTest = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient, ClientRole.CONSUMER);

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
