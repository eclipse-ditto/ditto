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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publish.GenericMqttPublish;
import org.junit.Before;
import org.junit.Test;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

import akka.stream.javadsl.Source;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SubscribeSuccess}.
 */
public final class SubscribeSuccessTest {

    private static final MqttTopic MQTT_TOPIC_SOURCE_STATUS = MqttTopic.of("source/status");
    private static final MqttTopic MQTT_TOPIC_SOURCE_TEMPERATURE = MqttTopic.of("source/thermostat/temperature");

    private List<MqttTopicFilter> mqttTopicFilters;

    @Before
    public void before() {
        mqttTopicFilters = Stream.of(MQTT_TOPIC_SOURCE_STATUS, MQTT_TOPIC_SOURCE_TEMPERATURE)
                .map(MqttTopic::toString)
                .map(MqttTopicFilter::of)
                .toList();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubscribeSuccess.class)
                .usingGetClass()
                .withPrefabValues(Source.class,
                        Source.single(
                                GenericMqttPublish.builder(
                                        MQTT_TOPIC_SOURCE_STATUS,
                                        MqttQos.AT_LEAST_ONCE
                                ).build()
                        ),
                        Source.single(
                                GenericMqttPublish.builder(
                                        MQTT_TOPIC_SOURCE_TEMPERATURE,
                                        MqttQos.AT_MOST_ONCE
                                ).build()
                        ))
                .verify();
    }

    @Test
    public void newInstanceWithNullMqttTopicFiltersThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SubscribeSuccess.newInstance(null, Source.empty()))
                .withMessage("The mqttTopicFilters must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithEmptyMqttTopicFiltersThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SubscribeSuccess.newInstance(Collections.emptyList(), Source.empty()))
                .withMessage("The argument 'mqttTopicFilters' must not be empty!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullMqttPublishSourceThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SubscribeSuccess.newInstance(mqttTopicFilters, null))
                .withMessage("The mqttPublishSource must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqttTopicFiltersReturnsExpected() {
        final var underTest = SubscribeSuccess.newInstance(mqttTopicFilters, Source.empty());

        assertThat(underTest.getMqttTopicFilters()).hasSameElementsAs(mqttTopicFilters);
    }

    @Test
    public void isSuccessReturnsTrue() {
        final var underTest = SubscribeSuccess.newInstance(mqttTopicFilters, Source.empty());

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void isFailureReturnsFalse() {
        final var underTest = SubscribeSuccess.newInstance(mqttTopicFilters, Source.empty());

        assertThat(underTest.isFailure()).isFalse();
    }

    @Test
    public void getMqttPublishSourceReturnsExpected() {
        final var mqttPublishSource = Source.fromJavaStream(
                () -> Stream.of(
                        GenericMqttPublish.builder(MQTT_TOPIC_SOURCE_STATUS, MqttQos.AT_LEAST_ONCE).build(),
                        GenericMqttPublish.builder(MQTT_TOPIC_SOURCE_TEMPERATURE, MqttQos.AT_MOST_ONCE).build()
                )
        );
        final var underTest = SubscribeSuccess.newInstance(mqttTopicFilters, mqttPublishSource);

        assertThat(underTest.getMqttPublishSourceOrThrow()).isEqualTo(mqttPublishSource);
    }

    @Test
    public void getErrorOrThrowThrowsException() {
        final var underTest = SubscribeSuccess.newInstance(mqttTopicFilters, Source.empty());

        assertThatIllegalStateException()
                .isThrownBy(underTest::getErrorOrThrow)
                .withMessage("Success cannot provide an error.")
                .withNoCause();
    }

}