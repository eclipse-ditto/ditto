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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.connectivity.model.Source;
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

/**
 * Unit test for {@link MqttSubscriber}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MqttSubscriberTest {

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance();

    @Mock
    private GenericMqttSubscribingClient subscribingClient;

    private ActorSystem actorSystem;

    @Before
    public void before() {
        actorSystem = actorSystemResource.getActorSystem();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MqttSubscriber.class, areImmutable());
    }

    @Test
    public void subscribeForConnectionSourceWithNullConnectionSourceThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttSubscriber.subscribeForConnectionSources(null, subscribingClient))
                .withMessage("The connectionSources must not be null!")
                .withNoCause();
    }

    @Test
    public void subscribeForConnectionSourceWithNullGenericMqttSubscribingClientThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttSubscriber.subscribeForConnectionSources(Collections.emptyList(), null))
                .withMessage("The genericMqttSubscribingClient must not be null!")
                .withNoCause();
    }

    @Test
    public void subscribeForConnectionSourceWithEmptyCollectionReturnsEmptySource() {
        final var subscribeResultSource =
                MqttSubscriber.subscribeForConnectionSources(Collections.emptyList(), subscribingClient);

        subscribeResultSource.runWith(TestSink.probe(actorSystem), actorSystem).expectSubscriptionAndComplete();
    }

    @Test
    public void subscribeForConnectionSourcesWithValidSourceAddressesTriggersSubscribingViaClient() {
        Mockito.when(subscribingClient.subscribe(Mockito.any(GenericMqttSubscribe.class)))
                .thenReturn(akka.stream.javadsl.Source.empty());
        final var mqttQos = MqttQos.AT_LEAST_ONCE;
        final var connectionSource1Addresses = Set.of("foo", "bar");
        final var connectionSource1 = mockConnectionSource(connectionSource1Addresses, mqttQos);
        final var connectionSource2Addresses = Set.of("baz");
        final var connectionSource2 = mockConnectionSource(connectionSource2Addresses, mqttQos);

        final var subscribeResultSource = MqttSubscriber.subscribeForConnectionSources(
                List.of(connectionSource1, connectionSource2),
                subscribingClient
        );

        subscribeResultSource.runWith(TestSink.probe(actorSystem), actorSystem).expectSubscriptionAndComplete();
        Mockito.verify(subscribingClient).subscribe(getGenericMqttSubscribe(connectionSource1Addresses, mqttQos));
        Mockito.verify(subscribingClient).subscribe(getGenericMqttSubscribe(connectionSource2Addresses, mqttQos));
    }

    private static Source mockConnectionSource(final Set<String> sourceAddresses, final MqttQos mqttQos) {
        final var result = Mockito.mock(Source.class);
        Mockito.when(result.getAddresses()).thenReturn(sourceAddresses);
        Mockito.when(result.getQos()).thenReturn(Optional.of(mqttQos.getCode()));
        return result;
    }

    private static GenericMqttSubscribe getGenericMqttSubscribe(final Collection<String> sourceAddresses,
            final MqttQos mqttQos) {

        return GenericMqttSubscribe.of(sourceAddresses.stream()
                .map(MqttTopicFilter::of)
                .map(mqttTopicFilter -> GenericMqttSubscription.newInstance(mqttTopicFilter, mqttQos))
                .collect(Collectors.toSet()));
    }

    @Test
    public void subscribeForConnectionSourcesWithInvalidSourceAddressReturnsSourceWithSubscribeFailure() {
        final var subscribeResultSource = MqttSubscriber.subscribeForConnectionSources(
                List.of(mockConnectionSource(Set.of("#/#"), MqttQos.EXACTLY_ONCE)),
                subscribingClient
        );

        final var testKit = actorSystemResource.newTestKit();
        final var onCompleteMessage = "done";
        subscribeResultSource.to(Sink.actorRef(testKit.getRef(), onCompleteMessage)).run(actorSystem);

        assertThat(testKit.expectMsgClass(SourceSubscribeResult.class))
                .satisfies(subscribeFailure -> assertThat(subscribeFailure.getErrorOrThrow())
                        .isInstanceOf(MqttSubscribeException.class)
                        .hasMessageStartingWith("Failed to instantiate GenericMqttSubscribe: ")
                        .hasCauseInstanceOf(InvalidMqttTopicFilterStringException.class));
        testKit.expectMsg(onCompleteMessage);
    }

}