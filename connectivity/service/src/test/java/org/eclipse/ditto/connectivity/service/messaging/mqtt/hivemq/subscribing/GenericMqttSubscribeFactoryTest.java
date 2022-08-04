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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscription;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

/**
 * Unit test for {@link GenericMqttSubscribeFactory}.
 */
public final class GenericMqttSubscribeFactoryTest {

    private static final String CONNECTION_NAME = GenericMqttSubscribeFactory.class.getSimpleName();

    @Test
    public void assertImmutability() {
        assertInstancesOf(GenericMqttSubscribeFactory.class,
                areImmutable(),
                provided(Function.class).isAlsoImmutable());
    }

    @Test
    public void getSourceSubscribeMessageForNullConnectionSourceThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttSubscribeFactory.getGenericSourceSubscribeMessage(null))
                .withMessage("The connectionSource must not be null!")
                .withNoCause();
    }

    @Test
    public void getSourceSubscribeMessageForConnectionSourceWithoutAddressesReturnsEmptyOptional()
            throws InvalidMqttTopicFilterStringException {

        final var connectionSource = Mockito.mock(Source.class);
        Mockito.when(connectionSource.getAddresses()).thenReturn(Collections.emptySet());

        assertThat(GenericMqttSubscribeFactory.getGenericSourceSubscribeMessage(connectionSource)).isEmpty();
    }

    @Test
    public void getSourceSubscribeMessageForConnectionSourceWithValidAddressesReturnsOptionalContainingExpected()
            throws InvalidMqttTopicFilterStringException {

        final var sourceAddresses =
                Set.of(CONNECTION_NAME + "/foo", CONNECTION_NAME + "/bar", CONNECTION_NAME + "/baz");
        final var mqttQos = MqttQos.AT_LEAST_ONCE;
        final var connectionSource = Mockito.mock(Source.class);
        Mockito.when(connectionSource.getAddresses()).thenReturn(sourceAddresses);
        Mockito.when(connectionSource.getQos()).thenReturn(Optional.of(mqttQos.getCode()));

        assertThat(GenericMqttSubscribeFactory.getGenericSourceSubscribeMessage(connectionSource))
                .hasValue(GenericMqttSubscribe.of(sourceAddresses.stream()
                        .map(MqttTopicFilter::of)
                        .map(mqttTopicFilter -> GenericMqttSubscription.newInstance(mqttTopicFilter, mqttQos))
                        .collect(Collectors.toSet())));
    }

    @Test
    public void getSourceSubscribeMessageForConnectionSourceWithInvalidAddressThrowsException() {
        final var invalidMqttTopicFilter = "#/#";
        final var sourceAddresses = new LinkedHashSet<String>();
        Collections.addAll(sourceAddresses, CONNECTION_NAME + "/foo", CONNECTION_NAME + "/bar", invalidMqttTopicFilter);
        final var mqttQos = MqttQos.AT_LEAST_ONCE;
        final var connectionSource = Mockito.mock(Source.class);
        Mockito.when(connectionSource.getAddresses()).thenReturn(sourceAddresses);
        Mockito.when(connectionSource.getQos()).thenReturn(Optional.of(mqttQos.getCode()));

        assertThatExceptionOfType(InvalidMqttTopicFilterStringException.class)
                .isThrownBy(() -> GenericMqttSubscribeFactory.getGenericSourceSubscribeMessage(connectionSource))
                .withMessage("""
                                Failed to instantiate MqttTopicFilter for <%s>: \
                                Topic filter [%s] contains misplaced wildcard characters. \
                                Multi level wildcard (#) must be the last character.\
                                """,
                        invalidMqttTopicFilter,
                        invalidMqttTopicFilter)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

}