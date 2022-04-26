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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publish.GenericMqttPublish;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3SubAckException;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.Mqtt5RxClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import akka.actor.ActorSystem;
import akka.stream.javadsl.Sink;
import io.reactivex.Single;

/**
 * Unit test for {@link GenericMqttSubscribingClient}.
 */
@RunWith(Enclosed.class)
public final class GenericMqttSubscribingClientTest {

    @RunWith(MockitoJUnitRunner.class)
    public static final class Mqtt3RxSubscribingClientTest {

        @ClassRule
        public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

        private static ActorSystem actorSystem;

        @BeforeClass
        public static void beforeClass() {
            actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        }

        @Mock
        private Mqtt3RxClient mqtt3RxClient;

        @Test
        public void ofMqtt3RxClientWithNullClientThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttSubscribingClient.ofMqtt3RxClient(null))
                    .withMessage("The mqtt3RxClient must not be null!")
                    .withNoCause();
        }

        @Test
        public void subscribeWithNullGenericMqttSubscribeThrowsException() {
            final var underTest = GenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient);

            assertThatNullPointerException()
                    .isThrownBy(() -> underTest.subscribe(null))
                    .withMessage("The genericMqttSubscribe must not be null!")
                    .withNoCause();
        }

        @Test
        public void subscribeWhenSubAckHasOnlySuccessReturnCodeWorksAsExpected() {
            final var mqtt3SubAck = Mockito.mock(Mqtt3SubAck.class);
            Mockito.when(mqtt3SubAck.getReturnCodes()).thenReturn(List.of(Mqtt3SubAckReturnCode.SUCCESS_MAXIMUM_QOS_1));
            Mockito.when(mqtt3RxClient.subscribe(Mockito.any(Mqtt3Subscribe.class)))
                    .thenReturn(Single.just(mqtt3SubAck));
            final var mqttTopicFilter = MqttTopicFilter.of("source/status");
            final var mqttQos = MqttQos.AT_LEAST_ONCE;
            final var mqtt3Publish = Mqtt3Publish.builder()
                    .topic(MqttTopic.of(mqttTopicFilter.toString()))
                    .qos(mqttQos)
                    .payload(ByteBufferUtils.fromUtf8String("online"))
                    .build();
            Mockito.when(mqtt3RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.SUBSCRIBED), Mockito.eq(true)))
                    .thenReturn(Single.just(mqtt3Publish).toFlowable());
            final var onCompleteMessage = "done";
            final var underTest = GenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient);

            final var subscribeResultSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            underTest.subscribe(GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(mqttTopicFilter,
                            mqttQos))))
                    .to(Sink.actorRef(subscribeResultSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var subscribeSuccess = subscribeResultSourceTestKit.expectMsgClass(SubscribeSuccess.class);
            subscribeResultSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(subscribeSuccess.getMqttTopicFilters()).containsOnly(mqttTopicFilter);

            final var mqttPublishSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            subscribeSuccess.getMqttPublishSourceOrThrow()
                    .to(Sink.actorRef(mqttPublishSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var genericMqttPublish = mqttPublishSourceTestKit.expectMsgClass(GenericMqttPublish.class);
            mqttPublishSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(genericMqttPublish).isEqualTo(GenericMqttPublish.ofMqtt3Publish(mqtt3Publish));
        }

        @Test
        public void subscribeWhenSubAckHasOnlyFailureReturnCodeWorksAsExpected() {
            final var mqtt3SubAck = Mockito.mock(Mqtt3SubAck.class);
            final var mqtt3SubAckReturnCode = Mqtt3SubAckReturnCode.FAILURE;
            Mockito.when(mqtt3SubAck.getReturnCodes()).thenReturn(List.of(mqtt3SubAckReturnCode));

            // If all return codes are failures then the Single errors with
            // an Mqtt3SubAckException according to HiveMQ API doc.
            Mockito.when(mqtt3RxClient.subscribe(Mockito.any(Mqtt3Subscribe.class)))
                    .thenReturn(Single.error(new Mqtt3SubAckException(mqtt3SubAck, null, null)));
            final var mqttTopicFilter = MqttTopicFilter.of("source/status");
            final var mqttQos = MqttQos.AT_LEAST_ONCE;
            final var onCompleteMessage = "done";
            final var underTest = GenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient);

            final var subscribeResultSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            underTest.subscribe(GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(mqttTopicFilter,
                            mqttQos))))
                    .to(Sink.actorRef(subscribeResultSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var subscribeFailure = subscribeResultSourceTestKit.expectMsgClass(SubscribeFailure.class);
            subscribeResultSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(subscribeFailure.getErrorOrThrow())
                    .isInstanceOfSatisfying(AllSubscriptionsFailedException.class, allSubscriptionsFailedException -> {
                        assertThat(allSubscriptionsFailedException.getFailedSubscriptionStatuses())
                                .containsOnly(new SubscriptionStatus(mqttTopicFilter,
                                        GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(mqtt3SubAckReturnCode)));
                        assertThat(allSubscriptionsFailedException).hasNoCause();
                    });
        }

        @Test
        public void subscribeWhenSingleThrowsUnexpectedExceptionWorksAsExpected() {
            final var illegalStateException = new IllegalStateException("This was so expected.");
            Mockito.when(mqtt3RxClient.subscribe(Mockito.any(Mqtt3Subscribe.class)))
                    .thenReturn(Single.error(illegalStateException));
            final var onCompleteMessage = "done";
            final var underTest = GenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient);

            final var subscribeResultSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            underTest.subscribe(GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(
                            MqttTopicFilter.of("source/status"),
                            MqttQos.AT_LEAST_ONCE)
                    )))
                    .to(Sink.actorRef(subscribeResultSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var subscribeFailure = subscribeResultSourceTestKit.expectMsgClass(SubscribeFailure.class);
            subscribeResultSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(subscribeFailure.getErrorOrThrow())
                    .isInstanceOf(MqttSubscribeException.class)
                    .hasMessage(illegalStateException.getMessage())
                    .hasCause(illegalStateException);
        }

        @Test
        public void subscribeWhenSubAckHasOnlySuccessAndFailureReturnCodesWorksAsExpected() {
            final var mqtt3SubAck = Mockito.mock(Mqtt3SubAck.class);
            final var subAckFooReturnCode = Mqtt3SubAckReturnCode.FAILURE;
            final var subAckBarReturnCode = Mqtt3SubAckReturnCode.SUCCESS_MAXIMUM_QOS_1;
            Mockito.when(mqtt3SubAck.getReturnCodes()).thenReturn(List.of(subAckFooReturnCode, subAckBarReturnCode));
            Mockito.when(mqtt3RxClient.subscribe(Mockito.any(Mqtt3Subscribe.class)))
                    .thenReturn(Single.just(mqtt3SubAck));
            final var mqttTopicFilterFoo = MqttTopicFilter.of("source/foo");
            final var mqttQos = MqttQos.AT_LEAST_ONCE;
            final var onCompleteMessage = "done";
            final var underTest = GenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3RxClient);

            final var genericMqttSubscriptions = new LinkedHashSet<GenericMqttSubscription>();
            genericMqttSubscriptions.add(GenericMqttSubscription.newInstance(mqttTopicFilterFoo, mqttQos));
            genericMqttSubscriptions.add(GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/bar"),
                    mqttQos));
            final var subscribeResultSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            underTest.subscribe(GenericMqttSubscribe.of(genericMqttSubscriptions))
                    .to(Sink.actorRef(subscribeResultSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var subscribeFailure = subscribeResultSourceTestKit.expectMsgClass(SubscribeFailure.class);
            subscribeResultSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(subscribeFailure.getErrorOrThrow())
                    .isInstanceOfSatisfying(SomeSubscriptionsFailedException.class,
                            someSubscriptionsFailedException -> {
                                assertThat(someSubscriptionsFailedException.getFailedSubscriptionStatuses())
                                        .containsOnly(new SubscriptionStatus(mqttTopicFilterFoo,
                                                GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(subAckFooReturnCode)));
                                assertThat(someSubscriptionsFailedException).hasNoCause();
                            });
        }

    }

    @RunWith(MockitoJUnitRunner.class)
    public static final class Mqtt5RxSubscribingClientTest {

        @ClassRule
        public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

        private static ActorSystem actorSystem;

        @BeforeClass
        public static void beforeClass() {
            actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        }

        @Mock
        private Mqtt5RxClient mqtt5RxClient;

        @Test
        public void ofMqtt5RxClientWithNullClientThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttSubscribingClient.ofMqtt5RxClient(null))
                    .withMessage("The mqtt5RxClient must not be null!")
                    .withNoCause();
        }

        @Test
        public void subscribeWithNullGenericMqttSubscribeThrowsException() {
            final var underTest = GenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient);

            assertThatNullPointerException()
                    .isThrownBy(() -> underTest.subscribe(null))
                    .withMessage("The genericMqttSubscribe must not be null!")
                    .withNoCause();
        }

        @Test
        public void subscribeWhenSubAckHasOnlySuccessReturnCodeWorksAsExpected() {
            final var mqtt5SubAck = Mockito.mock(Mqtt5SubAck.class);
            Mockito.when(mqtt5SubAck.getReasonCodes()).thenReturn(List.of(Mqtt5SubAckReasonCode.GRANTED_QOS_1));
            Mockito.when(mqtt5RxClient.subscribe(Mockito.any(Mqtt5Subscribe.class)))
                    .thenReturn(Single.just(mqtt5SubAck));
            final var mqttTopicFilter = MqttTopicFilter.of("source/status");
            final var mqttQos = MqttQos.AT_LEAST_ONCE;
            final var mqtt5Publish = Mqtt5Publish.builder()
                    .topic(MqttTopic.of(mqttTopicFilter.toString()))
                    .qos(mqttQos)
                    .payload(ByteBufferUtils.fromUtf8String("online"))
                    .build();
            Mockito.when(mqtt5RxClient.publishes(Mockito.eq(MqttGlobalPublishFilter.SUBSCRIBED), Mockito.eq(true)))
                    .thenReturn(Single.just(mqtt5Publish).toFlowable());
            final var onCompleteMessage = "done";
            final var underTest = GenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient);

            final var subscribeResultSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            underTest.subscribe(GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(mqttTopicFilter,
                            mqttQos))))
                    .to(Sink.actorRef(subscribeResultSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var subscribeSuccess = subscribeResultSourceTestKit.expectMsgClass(SubscribeSuccess.class);
            subscribeResultSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(subscribeSuccess.getMqttTopicFilters()).containsOnly(mqttTopicFilter);

            final var mqttPublishSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            subscribeSuccess.getMqttPublishSourceOrThrow()
                    .to(Sink.actorRef(mqttPublishSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var genericMqttPublish = mqttPublishSourceTestKit.expectMsgClass(GenericMqttPublish.class);
            mqttPublishSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(genericMqttPublish).isEqualTo(GenericMqttPublish.ofMqtt5Publish(mqtt5Publish));
        }

        @Test
        public void subscribeWhenSubAckHasOnlyFailureReturnCodeWorksAsExpected() {
            final var mqtt5SubAck = Mockito.mock(Mqtt5SubAck.class);
            final var mqtt5SubAckReasonCode = Mqtt5SubAckReasonCode.QUOTA_EXCEEDED;
            Mockito.when(mqtt5SubAck.getReasonCodes()).thenReturn(List.of(mqtt5SubAckReasonCode));

            // If all reason codes are failures then the Single errors with
            // an Mqtt5SubAckException according to HiveMQ API doc.
            Mockito.when(mqtt5RxClient.subscribe(Mockito.any(Mqtt5Subscribe.class)))
                    .thenReturn(Single.error(new Mqtt5SubAckException(mqtt5SubAck, "The quota seems to be exceeded.")));
            final var mqttTopicFilter = MqttTopicFilter.of("source/status");
            final var mqttQos = MqttQos.AT_LEAST_ONCE;
            final var onCompleteMessage = "done";
            final var underTest = GenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient);

            final var subscribeResultSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            underTest.subscribe(GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(mqttTopicFilter,
                            mqttQos))))
                    .to(Sink.actorRef(subscribeResultSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var subscribeFailure = subscribeResultSourceTestKit.expectMsgClass(SubscribeFailure.class);
            subscribeResultSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(subscribeFailure.getErrorOrThrow())
                    .isInstanceOfSatisfying(AllSubscriptionsFailedException.class, allSubscriptionsFailedException -> {
                        assertThat(allSubscriptionsFailedException.getFailedSubscriptionStatuses())
                                .containsOnly(new SubscriptionStatus(mqttTopicFilter,
                                        GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(mqtt5SubAckReasonCode)));
                        assertThat(allSubscriptionsFailedException).hasNoCause();
                    });
        }

        @Test
        public void subscribeWhenSingleThrowsUnexpectedExceptionWorksAsExpected() {
            final var illegalStateException = new IllegalStateException("This was so expected.");
            Mockito.when(mqtt5RxClient.subscribe(Mockito.any(Mqtt5Subscribe.class)))
                    .thenReturn(Single.error(illegalStateException));
            final var onCompleteMessage = "done";
            final var underTest = GenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient);

            final var subscribeResultSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            underTest.subscribe(GenericMqttSubscribe.of(Set.of(GenericMqttSubscription.newInstance(
                            MqttTopicFilter.of("source/status"),
                            MqttQos.AT_LEAST_ONCE)
                    )))
                    .to(Sink.actorRef(subscribeResultSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var subscribeFailure = subscribeResultSourceTestKit.expectMsgClass(SubscribeFailure.class);
            subscribeResultSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(subscribeFailure.getErrorOrThrow())
                    .isInstanceOf(MqttSubscribeException.class)
                    .hasMessage(illegalStateException.getMessage())
                    .hasCause(illegalStateException);
        }

        @Test
        public void subscribeWhenSubAckHasOnlySuccessAndFailureReturnCodesWorksAsExpected() {
            final var mqtt5SubAck = Mockito.mock(Mqtt5SubAck.class);
            final var subAckFooReasonCode = Mqtt5SubAckReasonCode.NOT_AUTHORIZED;
            final var subAckBarReasonCode = Mqtt5SubAckReasonCode.GRANTED_QOS_1;
            Mockito.when(mqtt5SubAck.getReasonCodes()).thenReturn(List.of(subAckFooReasonCode, subAckBarReasonCode));
            Mockito.when(mqtt5RxClient.subscribe(Mockito.any(Mqtt5Subscribe.class)))
                    .thenReturn(Single.just(mqtt5SubAck));
            final var mqttTopicFilterFoo = MqttTopicFilter.of("source/foo");
            final var mqttQos = MqttQos.AT_LEAST_ONCE;
            final var onCompleteMessage = "done";
            final var underTest = GenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5RxClient);

            final var genericMqttSubscriptions = new LinkedHashSet<GenericMqttSubscription>();
            genericMqttSubscriptions.add(GenericMqttSubscription.newInstance(mqttTopicFilterFoo, mqttQos));
            genericMqttSubscriptions.add(GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/bar"),
                    mqttQos));
            final var subscribeResultSourceTestKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
            underTest.subscribe(GenericMqttSubscribe.of(genericMqttSubscriptions))
                    .to(Sink.actorRef(subscribeResultSourceTestKit.getRef(), onCompleteMessage))
                    .run(actorSystem);

            final var subscribeFailure = subscribeResultSourceTestKit.expectMsgClass(SubscribeFailure.class);
            subscribeResultSourceTestKit.expectMsg(onCompleteMessage);

            assertThat(subscribeFailure.getErrorOrThrow())
                    .isInstanceOfSatisfying(SomeSubscriptionsFailedException.class,
                            someSubscriptionsFailedException -> {
                                assertThat(someSubscriptionsFailedException.getFailedSubscriptionStatuses())
                                        .containsOnly(new SubscriptionStatus(mqttTopicFilterFoo,
                                                GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(subAckFooReasonCode)));
                                assertThat(someSubscriptionsFailedException).hasNoCause();
                            });
        }

    }

}